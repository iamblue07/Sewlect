package com.sewlect.stylist.service;

import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.persistence.config.JpaAuditingConfig;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.enums.Role;
import com.sewlect.identity.repository.UserRepository;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.domain.PromptQueryOutcome;
import com.sewlect.outfit.entity.AiPromptQuery;
import com.sewlect.outfit.enums.AiPromptQueryStatus;
import com.sewlect.outfit.repository.AiPromptQueryRepository;
import com.sewlect.stylist.domain.AssistantTurn;
import com.sewlect.stylist.domain.SearchProposal;
import com.sewlect.stylist.domain.TurnRecord;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.entity.StylistSearch;
import com.sewlect.stylist.enums.AssistantTurnState;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.enums.MessageRole;
import com.sewlect.stylist.enums.StylistSearchStatus;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.repository.StylistMessageRepository;
import com.sewlect.stylist.repository.StylistSearchRepository;
import com.sewlect.stylist.support.SearchProposalCodec;
import com.sewlect.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({JpaAuditingConfig.class, StylistPersistenceService.class, SearchProposalCodec.class,
        UserReferenceQueryService.class, StylistPersistenceServiceTest.Beans.class})
class StylistPersistenceServiceTest extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    static class Beans {

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    private StylistPersistenceService service;
    @Autowired
    private StylistConversationRepository conversationRepository;
    @Autowired
    private StylistMessageRepository messageRepository;
    @Autowired
    private StylistSearchRepository searchRepository;
    @Autowired
    private AiPromptQueryRepository aiPromptQueryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SearchProposalCodec codec;
    @Autowired
    private EntityManager entityManager;

    private User user;

    private final AssistantTurn clarifying =
            AssistantTurn.withoutProposal(AssistantTurnState.CLARIFYING, "What is the occasion?");

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("stylist-" + UUID.randomUUID() + "@example.com")
                .passwordHash("{noop}irrelevant")
                .role(Role.USER)
                .build());
    }

    @Test
    void createWithTurn_storesAnOpenConversationWithTheUserAndAssistantMessages() {
        TurnRecord record = service.createWithTurn(user.getId(), "summer wedding", proposing("Wedding looks"));
        UUID conversationId = record.assistantMessage().getConversation().getId();
        flushAndClear();

        StylistConversation conversation = conversationRepository.findById(conversationId).orElseThrow();
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.OPEN);
        assertThat(conversation.getTitle()).isEqualTo("Wedding looks");
        assertThat(conversation.getCreatedAt()).isNotNull();

        List<StylistMessage> messages = messageRepository.findByConversationIdOrderBySequenceAsc(conversationId);
        assertThat(messages).extracting(StylistMessage::getSequence).containsExactly(0, 1);
        assertThat(messages).extracting(StylistMessage::getRole).containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(messages.get(0).getProposal()).isNull();
        assertThat(codec.decode(messages.get(1).getProposal())).isEqualTo(proposal());
    }

    @Test
    void createWithTurn_clarifyingTurn_leavesTheTitleEmptyAndStoresNoProposal() {
        UUID conversationId = createConversation();
        flushAndClear();

        assertThat(conversationRepository.findById(conversationId).orElseThrow().getTitle()).isNull();
        assertThat(messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId).orElseThrow()
                .getProposal()).isNull();
    }

    @Test
    void appendTurn_appendsBothMessagesAtTheGivenSequenceAndRetitlesOnAProposal() {
        UUID conversationId = createConversation();

        TurnRecord record = service.appendTurn(conversationId, 2, "for my brother", proposing("Brother's suit"));
        flushAndClear();

        assertThat(record.userMessage().getSequence()).isEqualTo(2);
        assertThat(record.assistantMessage().getSequence()).isEqualTo(3);
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getTitle()).isEqualTo("Brother's suit");
        assertThat(messageRepository.countByConversationIdAndRole(conversationId, MessageRole.USER)).isEqualTo(2);
    }

    @Test
    void appendTurn_withoutUserContent_appendsOnlyTheAssistantMessage() {
        UUID conversationId = createConversation();

        TurnRecord record = service.appendTurn(conversationId, 2, null, clarifying);
        flushAndClear();

        assertThat(record.userMessage()).isNull();
        assertThat(record.assistantMessage().getSequence()).isEqualTo(2);
        assertThat(messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)).hasSize(3);
    }

    @Test
    void appendTurn_conversationNotOpen_throwsConflict() {
        UUID conversationId = createConversation();
        service.startExecution(conversationId, latestMessageId(conversationId), proposal());

        assertThatThrownBy(() -> service.appendTurn(conversationId, 2, "hi", clarifying))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void appendTurn_sequenceAlreadyTakenByAConcurrentTurn_throwsConflict() {
        UUID conversationId = createConversation();
        flushAndClear();

        assertThatThrownBy(() -> service.appendTurn(conversationId, 1, "stale view", clarifying))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("reload");
    }

    @Test
    void startExecution_movesToExecutingAndCreatesOnePendingSearchFromTheSummary() {
        UUID conversationId = createConversation();
        UUID proposalMessageId = latestMessageId(conversationId);

        service.startExecution(conversationId, proposalMessageId, proposal());
        flushAndClear();

        assertThat(statusOf(conversationId)).isEqualTo(ConversationStatus.EXECUTING);
        assertThat(searchRepository.findByConversationIdOrderByPositionAsc(conversationId)).singleElement()
                .satisfies(search -> {
                    assertThat(search.getPosition()).isZero();
                    assertThat(search.getTitle()).isEqualTo("Wedding looks");
                    assertThat(search.getPrompt()).isEqualTo("a blue linen dress for a summer wedding");
                    assertThat(search.isMatchProfile()).isTrue();
                    assertThat(search.getStatus()).isEqualTo(StylistSearchStatus.PENDING);
                });
    }

    @Test
    void startExecution_secondConfirm_throwsConflict() {
        UUID conversationId = createConversation();
        UUID proposalMessageId = latestMessageId(conversationId);
        service.startExecution(conversationId, proposalMessageId, proposal());

        assertThatThrownBy(() -> service.startExecution(conversationId, proposalMessageId, proposal()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void revertToOpen_removesThePendingSearchesAndReopens() {
        UUID conversationId = createConversation();
        service.startExecution(conversationId, latestMessageId(conversationId), proposal());

        service.revertToOpen(conversationId);
        flushAndClear();

        assertThat(statusOf(conversationId)).isEqualTo(ConversationStatus.OPEN);
        assertThat(searchRepository.findByConversationIdOrderByPositionAsc(conversationId)).isEmpty();
    }

    @Test
    void recordSearchOutcome_linksTheQueryOnceAndRefusesASecondRecord() {
        UUID conversationId = createConversation();
        StylistSearch search = service.startExecution(conversationId, latestMessageId(conversationId), proposal());
        AiPromptQuery query = persistQuery(AiPromptQueryStatus.SUCCESS);
        PromptQueryOutcome outcome = new PromptQueryOutcome(query.getId(), AiPromptQueryStatus.SUCCESS, null, List.of());

        boolean first = service.recordSearchOutcome(search.getId(), outcome);
        boolean second = service.recordSearchOutcome(search.getId(), outcome);
        flushAndClear();

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        StylistSearch stored = searchRepository.findById(search.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(StylistSearchStatus.SUCCEEDED);
        assertThat(stored.getAiPromptQueryId()).isEqualTo(query.getId());
    }

    @Test
    void finishExecution_searchSucceeded_completesTheConversation() {
        UUID conversationId = createConversation();
        StylistSearch search = service.startExecution(conversationId, latestMessageId(conversationId), proposal());
        AiPromptQuery query = persistQuery(AiPromptQueryStatus.SUCCESS);
        service.recordSearchOutcome(search.getId(),
                new PromptQueryOutcome(query.getId(), AiPromptQueryStatus.SUCCESS, null, List.of()));

        assertThat(service.finishExecution(conversationId)).isTrue();
        flushAndClear();

        StylistConversation conversation = conversationRepository.findById(conversationId).orElseThrow();
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.COMPLETE);
        assertThat(conversation.getCompletedAt()).isNotNull();
        assertThat(searchRepository.findByConversationIdOrderByPositionAsc(conversationId))
                .extracting(StylistSearch::getStatus)
                .containsExactly(StylistSearchStatus.SUCCEEDED);
    }

    @Test
    void finishExecution_searchStillPending_failsItAndEndsTheConversationFailed() {
        UUID conversationId = createConversation();
        service.startExecution(conversationId, latestMessageId(conversationId), proposal());

        service.finishExecution(conversationId);
        flushAndClear();

        assertThat(statusOf(conversationId)).isEqualTo(ConversationStatus.FAILED);
        assertThat(searchRepository.findByConversationIdOrderByPositionAsc(conversationId))
                .extracting(StylistSearch::getStatus)
                .containsExactly(StylistSearchStatus.FAILED);
    }

    @Test
    void finishExecution_noneSucceeded_endsFailed() {
        UUID conversationId = createConversation();
        StylistSearch search = service.startExecution(conversationId, latestMessageId(conversationId), proposal());
        AiPromptQuery failed = persistQuery(AiPromptQueryStatus.FAILED);
        service.recordSearchOutcome(search.getId(),
                new PromptQueryOutcome(failed.getId(), AiPromptQueryStatus.FAILED, "boom", List.of()));

        service.finishExecution(conversationId);
        flushAndClear();

        assertThat(statusOf(conversationId)).isEqualTo(ConversationStatus.FAILED);
    }

    @Test
    void failExecution_onlyAppliesWhileExecuting() {
        UUID conversationId = createConversation();

        assertThat(service.failExecution(conversationId)).isFalse();

        service.startExecution(conversationId, latestMessageId(conversationId), proposal());
        assertThat(service.failExecution(conversationId)).isTrue();
        assertThat(service.failExecution(conversationId)).isFalse();
        flushAndClear();

        assertThat(statusOf(conversationId)).isEqualTo(ConversationStatus.FAILED);
        assertThat(searchRepository.findByConversationIdOrderByPositionAsc(conversationId))
                .extracting(StylistSearch::getStatus).containsOnly(StylistSearchStatus.FAILED);
    }

    @Test
    void touchExecuting_onlySucceedsWhileExecuting() {
        UUID conversationId = createConversation();

        assertThat(service.touchExecuting(conversationId)).isFalse();

        service.startExecution(conversationId, latestMessageId(conversationId), proposal());
        assertThat(service.touchExecuting(conversationId)).isTrue();
    }

    @Test
    void deleteUnlessExecuting_refusesWhileExecuting() {
        UUID conversationId = createConversation();
        service.startExecution(conversationId, latestMessageId(conversationId), proposal());

        assertThat(service.deleteUnlessExecuting(conversationId)).isFalse();
        flushAndClear();

        assertThat(conversationRepository.findById(conversationId)).isPresent();
    }

    @Test
    void deleteUnlessExecuting_cascadesToMessagesAndSearchesButKeepsTheEvaluationRows() {
        UUID conversationId = createConversation();
        StylistSearch search = service.startExecution(conversationId, latestMessageId(conversationId), proposal());
        AiPromptQuery query = persistQuery(AiPromptQueryStatus.SUCCESS);
        service.recordSearchOutcome(search.getId(),
                new PromptQueryOutcome(query.getId(), AiPromptQueryStatus.SUCCESS, null, List.of()));
        service.finishExecution(conversationId);
        flushAndClear();

        assertThat(service.deleteUnlessExecuting(conversationId)).isTrue();
        flushAndClear();

        assertThat(conversationRepository.findById(conversationId)).isEmpty();
        assertThat(messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)).isEmpty();
        assertThat(searchRepository.findByConversationIdOrderByPositionAsc(conversationId)).isEmpty();
        assertThat(aiPromptQueryRepository.findById(query.getId())).isPresent();
    }

    private UUID createConversation() {
        TurnRecord record = service.createWithTurn(user.getId(), "summer wedding", clarifying);
        return record.assistantMessage().getConversation().getId();
    }

    private UUID latestMessageId(UUID conversationId) {
        entityManager.flush();
        return messageRepository.findFirstByConversationIdOrderBySequenceDesc(conversationId).orElseThrow().getId();
    }

    private ConversationStatus statusOf(UUID conversationId) {
        return conversationRepository.findById(conversationId).orElseThrow().getStatus();
    }

    private AiPromptQuery persistQuery(AiPromptQueryStatus status) {
        return aiPromptQueryRepository.save(AiPromptQuery.builder()
                .user(user)
                .rawPrompt("a prompt")
                .matchProfile(true)
                .status(status)
                .model("openai/gpt-oss-120b")
                .build());
    }

    private AssistantTurn proposing(String title) {
        return new AssistantTurn(AssistantTurnState.PROPOSING, "Shall I go ahead?", title, "A blue linen look for a summer wedding.", "a blue linen dress for a summer wedding", true);
    }

    private SearchProposal proposal() {
        return new SearchProposal("Wedding looks", "A blue linen look for a summer wedding.", "a blue linen dress for a summer wedding", true);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
