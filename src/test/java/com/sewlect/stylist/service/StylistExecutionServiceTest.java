package com.sewlect.stylist.service;

import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.exception.ServiceUnavailableException;
import com.sewlect.outfit.service.PromptGenerationQuotaService;
import com.sewlect.outfit.service.PromptResultQueryService;
import com.sewlect.stylist.domain.AssistantTurn;
import com.sewlect.stylist.domain.SearchProposal;
import com.sewlect.stylist.dto.ConversationSummaryResponse;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.AssistantTurnState;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.enums.MessageRole;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.repository.StylistMessageRepository;
import com.sewlect.stylist.repository.StylistSearchRepository;
import com.sewlect.stylist.support.SearchProposalCodec;
import com.sewlect.stylist.support.StylistResponseAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StylistExecutionServiceTest {

    @Mock
    private StylistConversationRepository conversationRepository;
    @Mock
    private StylistMessageRepository messageRepository;
    @Mock
    private StylistSearchRepository searchRepository;
    @Mock
    private StylistPersistenceService persistenceService;
    @Mock
    private StylistSearchExecutor searchExecutor;
    @Mock
    private PromptGenerationQuotaService quotaService;
    @Mock
    private PromptResultQueryService resultQueryService;
    @Mock
    private AsyncTaskExecutor stylistExecutor;

    private StylistExecutionService service;
    private SearchProposalCodec codec;

    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
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
        service = new StylistExecutionService(queryService, messageRepository, persistenceService,
                searchExecutor, quotaService, codec, assembler, stylistExecutor);
    }

    @Test
    void confirm_messageIsNotTheLatest_throwsConflictWithoutConsumingBudget() {
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId))
                .thenReturn(Optional.of(message(conversation, 5, MessageRole.ASSISTANT, "newer", proposalJson())));

        assertThatThrownBy(() -> service.confirm(userId, conversationId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer current");
        verifyNoInteractions(quotaService);
        verify(persistenceService, never()).startExecution(any(), any(), any());
    }

    @Test
    void confirm_latestMessageCarriesNoProposal_throwsConflict() {
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        StylistMessage latest = message(conversation, 3, MessageRole.ASSISTANT, "question", null);
        when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.confirm(userId, conversationId, latest.getId()))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(quotaService);
    }

    @Test
    void confirm_conversationAlreadyExecuting_throwsConflictWithoutConsumingBudget() {
        owned(ConversationStatus.EXECUTING);

        assertThatThrownBy(() -> service.confirm(userId, conversationId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("closed");
        verifyNoInteractions(quotaService, messageRepository);
    }

    @Test
    void confirm_generationBudgetRefused_leavesTheConversationOpen() {
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        StylistMessage latest = message(conversation, 3, MessageRole.ASSISTANT, "Ideas", proposalJson());
        when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)).thenReturn(Optional.of(latest));
        doThrow(new RateLimitExceededException("over budget")).when(quotaService).consumeGeneration(userId, 1);

        assertThatThrownBy(() -> service.confirm(userId, conversationId, latest.getId()))
                .isInstanceOf(RateLimitExceededException.class);
        verify(persistenceService, never()).startExecution(any(), any(), any());
        verifyNoInteractions(stylistExecutor);
    }

    @Test
    void confirm_consumesOneUnitPerSearchBeforeStartingAndSubmitsTheExecution() {
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        StylistMessage latest = message(conversation, 3, MessageRole.ASSISTANT, "Ideas", proposalJson());
        when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)).thenReturn(Optional.of(latest));
        when(conversationRepository.findByIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(conversation), Optional.of(conversation(ConversationStatus.EXECUTING)));

        ConversationSummaryResponse summary = service.confirm(userId, conversationId, latest.getId());

        InOrder order = inOrder(quotaService, persistenceService, stylistExecutor);
        order.verify(quotaService).consumeGeneration(userId, 1);
        order.verify(persistenceService).startExecution(eq(conversationId), eq(latest.getId()), any(SearchProposal.class));
        order.verify(stylistExecutor).submit(any(Runnable.class));
        assertThat(summary.status()).isEqualTo(ConversationStatus.EXECUTING);
    }

    @Test
    void confirm_startsTheSearchStoredOnTheConfirmedSummary() {
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        StylistMessage latest = message(conversation, 3, MessageRole.ASSISTANT, "Ideas", proposalJson());
        when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)).thenReturn(Optional.of(latest));

        service.confirm(userId, conversationId, latest.getId());

        ArgumentCaptor<SearchProposal> proposalCaptor = ArgumentCaptor.forClass(SearchProposal.class);
        verify(persistenceService).startExecution(eq(conversationId), eq(latest.getId()), proposalCaptor.capture());
        assertThat(proposalCaptor.getValue().prompt()).isEqualTo("a blue linen dress for a summer wedding");
        assertThat(proposalCaptor.getValue().matchProfile()).isTrue();
    }

    @Test
    void confirm_queueFull_revertsToOpenAndThrowsServiceUnavailable() {
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        StylistMessage latest = message(conversation, 3, MessageRole.ASSISTANT, "Ideas", proposalJson());
        when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)).thenReturn(Optional.of(latest));
        when(stylistExecutor.submit(any(Runnable.class))).thenThrow(new TaskRejectedException("queue full"));

        assertThatThrownBy(() -> service.confirm(userId, conversationId, latest.getId()))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(persistenceService).revertToOpen(conversationId);
        verify(persistenceService, never()).failExecution(any());
    }

    @Test
    void confirm_backgroundRunThrows_failsTheExecution() {
        StylistConversation conversation = owned(ConversationStatus.OPEN);
        StylistMessage latest = message(conversation, 3, MessageRole.ASSISTANT, "Ideas", proposalJson());
        when(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId)).thenReturn(Optional.of(latest));
        doThrow(new IllegalStateException("boom")).when(searchExecutor).run(conversationId);

        service.confirm(userId, conversationId, latest.getId());

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(stylistExecutor).submit(task.capture());
        task.getValue().run();
        verify(persistenceService).failExecution(conversationId);
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

    private SearchProposal proposal() {
        return new SearchProposal("Wedding looks", "A blue linen look for a summer wedding.", "a blue linen dress for a summer wedding", true);
    }

    private String proposalJson() {
        return codec.encode(proposal());
    }
}
