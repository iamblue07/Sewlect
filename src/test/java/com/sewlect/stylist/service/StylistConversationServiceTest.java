package com.sewlect.stylist.service;

import com.sewlect.common.ai.domain.SafetyVerdict;
import com.sewlect.common.ai.service.ContentSafetyService;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.exception.ContentPolicyViolationException;
import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.logging.support.SecurityEventLogger;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.outfit.service.PromptResultQueryService;
import com.sewlect.stylist.domain.AssistantTurn;
import com.sewlect.stylist.domain.SearchProposal;
import com.sewlect.stylist.domain.StylistPromptContext;
import com.sewlect.stylist.domain.TurnRecord;
import com.sewlect.stylist.dto.TurnResponse;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.AssistantTurnState;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.enums.MessageRole;
import com.sewlect.stylist.properties.StylistGuardrailProperties;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.repository.StylistMessageRepository;
import com.sewlect.stylist.repository.StylistSearchRepository;
import com.sewlect.stylist.support.SearchProposalCodec;
import com.sewlect.stylist.support.StylistInputSanitizer;
import com.sewlect.stylist.support.StylistPromptContextFactory;
import com.sewlect.stylist.support.StylistResponseAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StylistConversationServiceTest {

    @Mock
    private StylistConversationRepository conversationRepository;
    @Mock
    private StylistMessageRepository messageRepository;
    @Mock
    private StylistSearchRepository searchRepository;
    @Mock
    private StylistPersistenceService persistenceService;
    @Mock
    private StylistChatModelService chatModelService;
    @Mock
    private StylistPromptContextFactory contextFactory;
    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private ContentSafetyService contentSafetyService;
    @Mock
    private SecurityEventLogger securityEventLogger;
    @Mock
    private PromptResultQueryService resultQueryService;
    private StylistConversationService service;
    private SearchProposalCodec codec;

    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final StylistPromptContext context = new StylistPromptContext(null, null, List.of(), Map.of(), List.of());
    private final AssistantTurn clarifying =
            AssistantTurn.withoutProposal(AssistantTurnState.CLARIFYING, "What is the occasion?");

    @BeforeEach
    void setUp() {
        codec = new SearchProposalCodec(JsonMapper.builder().build());
        StylistProperties properties = new StylistProperties(
                15, 1000, 60, Duration.ofMinutes(10), Duration.ofMinutes(15), 300000L, 20, 100);
        StylistResponseAssembler assembler = new StylistResponseAssembler(codec, properties);
        StylistConversationQueryService queryService = new StylistConversationQueryService(conversationRepository,
                messageRepository, searchRepository, resultQueryService, assembler, properties);
        StylistGuardrailService guardrailService = new StylistGuardrailService(rateLimiter, contentSafetyService,
                new StylistInputSanitizer(), securityEventLogger, properties, new StylistGuardrailProperties(
                        1500, 60, 800, 400, 3, Duration.ofHours(24), Duration.ofHours(24), 200, 10,
                        "Outfits only."));
        service = new StylistConversationService(queryService, messageRepository, persistenceService,
                chatModelService, contextFactory, guardrailService, assembler, properties);
        lenient().when(contentSafetyService.checkUserMessage(any())).thenReturn(SafetyVerdict.SAFE);
    }

    @Test
    void start_messageBudgetExhausted_throwsRateLimitExceededAndCallsNothingElse() {
        when(rateLimiter.tryConsume(userId.toString(), "stylist-message", 60, Duration.ofHours(1))).thenReturn(false);

        assertThatThrownBy(() -> service.start(userId, "hello")).isInstanceOf(RateLimitExceededException.class);
        verifyNoInteractions(chatModelService, persistenceService);
    }

    @Test
    void start_messageLongerThanTheLimit_throwsBadRequestWithoutCallingTheModel() {
        allowMessages();

        assertThatThrownBy(() -> service.start(userId, "x".repeat(1001))).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(chatModelService, persistenceService);
    }

    @Test
    void start_callsTheModelWithEmptyHistoryAndStoresBothMessages() {
        allowMessages();
        when(contextFactory.build(userId)).thenReturn(context);
        when(chatModelService.nextTurn(context, List.of(), "summer wedding", false)).thenReturn(clarifying);
        StylistConversation conversation = conversation(ConversationStatus.OPEN);
        when(persistenceService.createWithTurn(userId, "summer wedding", clarifying)).thenReturn(new TurnRecord(
                message(conversation, 0, MessageRole.USER, "summer wedding", null),
                message(conversation, 1, MessageRole.ASSISTANT, "What is the occasion?", null)));

        TurnResponse response = service.start(userId, "summer wedding");

        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.status()).isEqualTo(ConversationStatus.OPEN);
        assertThat(response.userTurnsRemaining()).isEqualTo(14);
        assertThat(response.userMessage().content()).isEqualTo("summer wedding");
        assertThat(response.assistantMessage().content()).isEqualTo("What is the occasion?");
        assertThat(response.assistantMessage().proposal()).isNull();
    }

    @Test
    void sendMessage_anotherUsersConversation_throwsNotFound() {
        allowMessages();
        when(conversationRepository.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(userId, conversationId, "hi"))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(chatModelService);
    }

    @Test
    void sendMessage_closedConversation_throwsConflictWithoutCallingTheModel() {
        allowMessages();
        owned(ConversationStatus.EXECUTING);

        assertThatThrownBy(() -> service.sendMessage(userId, conversationId, "hi"))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(chatModelService);
    }

    @Test
    void sendMessage_turnLimitReached_throwsConflict() {
        allowMessages();
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        when(messageRepository.findByConversationIdOrderBySequenceAsc(conversationId))
                .thenReturn(historyWithUserTurns(conversation, 15));

        assertThatThrownBy(() -> service.sendMessage(userId, conversationId, "hi"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Turn limit reached");
        verifyNoInteractions(chatModelService);
    }

    @Test
    void sendMessage_appendsAtTheSequenceAfterTheHistoryTheModelSaw() {
        allowMessages();
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        List<StylistMessage> history = List.of(
                message(conversation, 0, MessageRole.USER, "a", null),
                message(conversation, 1, MessageRole.ASSISTANT, "b", null),
                message(conversation, 2, MessageRole.USER, "c", null),
                message(conversation, 3, MessageRole.ASSISTANT, "d", null));
        when(messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)).thenReturn(history);
        when(contextFactory.build(userId)).thenReturn(context);
        when(chatModelService.nextTurn(context, history, "for my brother", false)).thenReturn(clarifying);
        when(persistenceService.appendTurn(conversationId, 4, "for my brother", clarifying)).thenReturn(new TurnRecord(
                message(conversation, 4, MessageRole.USER, "for my brother", null),
                message(conversation, 5, MessageRole.ASSISTANT, "What is the occasion?", null)));

        TurnResponse response = service.sendMessage(userId, conversationId, "for my brother");

        assertThat(response.userTurnsRemaining()).isEqualTo(12);
        verify(persistenceService).appendTurn(conversationId, 4, "for my brother", clarifying);
    }

    @Test
    void requestProposal_ignoresTheTurnLimitAndAsksTheModelToProposeWithoutAUserMessage() {
        allowMessages();
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        List<StylistMessage> history = historyWithUserTurns(conversation, 15);
        when(messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)).thenReturn(history);
        when(contextFactory.build(userId)).thenReturn(context);
        AssistantTurn proposing = proposingTurn();
        when(chatModelService.nextTurn(context, history, null, true)).thenReturn(proposing);
        when(persistenceService.appendTurn(conversationId, 30, null, proposing)).thenReturn(new TurnRecord(null,
                message(conversation, 30, MessageRole.ASSISTANT, "Ideas", proposalJson())));

        TurnResponse response = service.requestProposal(userId, conversationId);

        assertThat(response.userMessage()).isNull();
        assertThat(response.userTurnsRemaining()).isZero();
        assertThat(response.assistantMessage().proposal().title()).isEqualTo("Wedding looks");
        assertThat(response.assistantMessage().proposal().summary()).isEqualTo("A blue linen look for a summer wedding.");
    }

    @Test
    void delete_whileExecuting_throwsConflict() {
        owned(ConversationStatus.EXECUTING);
        when(persistenceService.deleteUnlessExecuting(conversationId)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(userId, conversationId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_anotherUsersConversation_throwsNotFoundAndDeletesNothing() {
        when(conversationRepository.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, conversationId)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(persistenceService);
    }

    private void allowMessages() {
        when(rateLimiter.tryConsume(eq(userId.toString()), anyString(), anyInt(), any())).thenReturn(true);
    }

    private StylistConversation owned(ConversationStatus status) {
        StylistConversation conversation = conversation(status);
        when(conversationRepository.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        return conversation;
    }

    private StylistConversation conversation(ConversationStatus status) {
        return StylistConversation.builder().id(conversationId).status(status).title("Wedding looks").build();
    }

    private StylistMessage message(StylistConversation conversation, int sequence, MessageRole role, String content,
                                   String proposal) {
        return StylistMessage.builder().id(UUID.randomUUID()).conversation(conversation).sequence(sequence)
                .role(role).content(content).proposal(proposal).build();
    }

    private List<StylistMessage> historyWithUserTurns(StylistConversation conversation, int userTurns) {
        List<StylistMessage> history = new ArrayList<>(userTurns * 2);
        for (int turn = 0; turn < userTurns; turn++) {
            history.add(message(conversation, turn * 2, MessageRole.USER, "q" + turn, null));
            history.add(message(conversation, turn * 2 + 1, MessageRole.ASSISTANT, "a" + turn, null));
        }
        return history;
    }

    private AssistantTurn proposingTurn() {
        SearchProposal proposal = proposal();
        return new AssistantTurn(AssistantTurnState.PROPOSING, "Shall I go ahead?", proposal.title(),
                proposal.summary(), proposal.prompt(), proposal.matchProfile());
    }

    private SearchProposal proposal() {
        return new SearchProposal("Wedding looks", "A blue linen look for a summer wedding.", "a blue linen dress for a summer wedding", true);
    }

    private String proposalJson() {
        return codec.encode(proposal());
    }

    @Test
    void start_unsafeMessage_throws422AndNeverCallsTheModelOrStoresAnything() {
        allowMessages();
        when(contentSafetyService.checkUserMessage("harmful")).thenReturn(SafetyVerdict.unsafe(List.of("S1")));

        assertThatThrownBy(() -> service.start(userId, "harmful")).isInstanceOf(ContentPolicyViolationException.class);
        verifyNoInteractions(chatModelService, persistenceService, contextFactory);
        verify(securityEventLogger).stylistStrikeRecorded(userId, "BLOCKED_MESSAGE");
    }

    @Test
    void start_messageWithInvisibleCharacters_isSanitizedBeforeTheModelAndStorage() {
        allowMessages();
        when(contextFactory.build(userId)).thenReturn(context);
        when(chatModelService.nextTurn(context, List.of(), "summer wedding", false)).thenReturn(clarifying);
        StylistConversation conversation = conversation(ConversationStatus.OPEN);
        when(persistenceService.createWithTurn(userId, "summer wedding", clarifying)).thenReturn(new TurnRecord(
                message(conversation, 0, MessageRole.USER, "summer wedding", null),
                message(conversation, 1, MessageRole.ASSISTANT, "What is the occasion?", null)));

        service.start(userId, "  summer\u200B wedding ");

        verify(contentSafetyService).checkUserMessage("summer wedding");
        verify(persistenceService).createWithTurn(userId, "summer wedding", clarifying);
    }

    @Test
    void start_dailyChatLimitReached_throwsRateLimitExceededBeforeAnyModelCall() {
        allowMessages();
        when(rateLimiter.tryConsume(userId.toString(), "stylist-conversation-daily", 10, Duration.ofDays(1)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.start(userId, "hello")).isInstanceOf(RateLimitExceededException.class);
        verifyNoInteractions(chatModelService, contentSafetyService);
    }

    @Test
    void start_declinedTurn_isStoredReturnedAndRecordsAStrike() {
        allowMessages();
        AssistantTurn declined = AssistantTurn.withoutProposal(AssistantTurnState.DECLINED, "I can only help with outfits.");
        when(contextFactory.build(userId)).thenReturn(context);
        when(chatModelService.nextTurn(context, List.of(), "write me a poem", false)).thenReturn(declined);
        StylistConversation conversation = conversation(ConversationStatus.OPEN);
        when(persistenceService.createWithTurn(userId, "write me a poem", declined)).thenReturn(new TurnRecord(
                message(conversation, 0, MessageRole.USER, "write me a poem", null),
                message(conversation, 1, MessageRole.ASSISTANT, "I can only help with outfits.", null)));

        TurnResponse response = service.start(userId, "write me a poem");

        assertThat(response.assistantMessage().content()).isEqualTo("I can only help with outfits.");
        verify(securityEventLogger).stylistStrikeRecorded(userId, "DECLINED_TURN");
        verify(rateLimiter).tryConsume(userId.toString(), "stylist-strike", 3, Duration.ofHours(24));
    }

    @Test
    void start_clarifyingTurn_recordsNoStrike() {
        allowMessages();
        when(contextFactory.build(userId)).thenReturn(context);
        when(chatModelService.nextTurn(context, List.of(), "summer wedding", false)).thenReturn(clarifying);
        StylistConversation conversation = conversation(ConversationStatus.OPEN);
        when(persistenceService.createWithTurn(userId, "summer wedding", clarifying)).thenReturn(new TurnRecord(
                message(conversation, 0, MessageRole.USER, "summer wedding", null),
                message(conversation, 1, MessageRole.ASSISTANT, "What is the occasion?", null)));

        service.start(userId, "summer wedding");

        verifyNoInteractions(securityEventLogger);
    }

    @Test
    void sendMessage_pausedAccount_throwsRateLimitExceededBeforeAnythingElse() {
        when(rateLimiter.isExhausted(userId.toString(), "stylist-pause", 1)).thenReturn(true);

        assertThatThrownBy(() -> service.sendMessage(userId, conversationId, "hi"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("paused");
        verifyNoInteractions(conversationRepository, messageRepository, chatModelService, contentSafetyService);
    }

    @Test
    void sendMessage_unsafeMessage_throws422AfterTheOwnershipChecksAndStoresNothing() {
        allowMessages();
        owned(ConversationStatus.OPEN);
        when(messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)).thenReturn(List.of());
        when(contentSafetyService.checkUserMessage("harmful")).thenReturn(SafetyVerdict.unsafe(List.of("S10")));

        assertThatThrownBy(() -> service.sendMessage(userId, conversationId, "harmful"))
                .isInstanceOf(ContentPolicyViolationException.class);
        verifyNoInteractions(chatModelService, persistenceService);
    }

    @Test
    void sendMessage_turnLimitReached_neverSpendsASafetyCheck() {
        allowMessages();
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        when(messageRepository.findByConversationIdOrderBySequenceAsc(conversationId))
                .thenReturn(historyWithUserTurns(conversation, 15));

        assertThatThrownBy(() -> service.sendMessage(userId, conversationId, "hi")).isInstanceOf(ConflictException.class);
        verifyNoInteractions(contentSafetyService);
    }

    @Test
    void sendMessage_closedConversation_neverSpendsASafetyCheck() {
        allowMessages();
        owned(ConversationStatus.EXECUTING);

        assertThatThrownBy(() -> service.sendMessage(userId, conversationId, "hi")).isInstanceOf(ConflictException.class);
        verifyNoInteractions(contentSafetyService);
    }

    @Test
    void sendMessage_declinedTurn_recordsAStrike() {
        allowMessages();
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        List<StylistMessage> history = historyWithUserTurns(conversation, 1);
        when(messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)).thenReturn(history);
        AssistantTurn declined = AssistantTurn.withoutProposal(AssistantTurnState.DECLINED, "Outfits only.");
        when(contextFactory.build(userId)).thenReturn(context);
        when(chatModelService.nextTurn(context, history, "tell me a joke", false)).thenReturn(declined);
        when(persistenceService.appendTurn(conversationId, 2, "tell me a joke", declined)).thenReturn(new TurnRecord(
                message(conversation, 2, MessageRole.USER, "tell me a joke", null),
                message(conversation, 3, MessageRole.ASSISTANT, "Outfits only.", null)));

        service.sendMessage(userId, conversationId, "tell me a joke");

        verify(securityEventLogger).stylistStrikeRecorded(userId, "DECLINED_TURN");
    }

    @Test
    void requestProposal_hasNoUserTextSoSkipsTheInputSafetyCheck() {
        allowMessages();
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        List<StylistMessage> history = historyWithUserTurns(conversation, 1);
        when(messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)).thenReturn(history);
        when(contextFactory.build(userId)).thenReturn(context);
        when(chatModelService.nextTurn(context, history, null, true)).thenReturn(clarifying);
        when(persistenceService.appendTurn(conversationId, 2, null, clarifying)).thenReturn(new TurnRecord(null,
                message(conversation, 2, MessageRole.ASSISTANT, "What is the occasion?", null)));

        service.requestProposal(userId, conversationId);

        verifyNoInteractions(contentSafetyService);
    }
}
