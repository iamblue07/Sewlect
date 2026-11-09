package com.sewlect.stylist.service;

import com.sewlect.identity.entity.User;
import com.sewlect.outfit.domain.PromptQueryOutcome;
import com.sewlect.outfit.enums.AiPromptQueryStatus;
import com.sewlect.outfit.service.PromptOutfitGenerationService;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistSearch;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.enums.StylistSearchStatus;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.repository.StylistConversationRepository;
import com.sewlect.stylist.repository.StylistSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StylistSearchExecutorTest {

    private static final Instant START = Instant.parse("2026-11-09T12:00:00Z");

    @Mock
    private StylistConversationRepository conversationRepository;
    @Mock
    private StylistSearchRepository searchRepository;
    @Mock
    private StylistPersistenceService persistenceService;
    @Mock
    private PromptOutfitGenerationService generationService;
    @Mock
    private Clock clock;

    private StylistSearchExecutor executor;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        StylistProperties properties = new StylistProperties(
                15, 1000, 60, Duration.ofMinutes(10), Duration.ofMinutes(15), 300000L, 20, 100);
        executor = new StylistSearchExecutor(conversationRepository, searchRepository, persistenceService,
                generationService, properties, clock);
        StylistConversation conversation = StylistConversation.builder().id(conversationId)
                .user(User.builder().id(userId).build()).status(ConversationStatus.EXECUTING).build();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
    }

    @Test
    void run_executesEveryPendingSearchInOrderRecordsEachOutcomeThenFinishes() {
        when(clock.instant()).thenReturn(START);
        StylistSearch first = search(0, "a linen dress", true);
        StylistSearch second = search(1, "a suit for men around 100 EUR", false);
        stubPending(first, second);
        when(persistenceService.touchExecuting(conversationId)).thenReturn(true);
        PromptQueryOutcome firstOutcome = outcome(AiPromptQueryStatus.SUCCESS);
        PromptQueryOutcome secondOutcome = outcome(AiPromptQueryStatus.FAILED);
        when(generationService.execute(userId, "a linen dress", true)).thenReturn(firstOutcome);
        when(generationService.execute(userId, "a suit for men around 100 EUR", false)).thenReturn(secondOutcome);

        executor.run(conversationId);

        InOrder order = inOrder(persistenceService, generationService);
        order.verify(persistenceService).touchExecuting(conversationId);
        order.verify(generationService).execute(userId, "a linen dress", true);
        order.verify(persistenceService).recordSearchOutcome(first.getId(), firstOutcome);
        order.verify(persistenceService).touchExecuting(conversationId);
        order.verify(generationService).execute(userId, "a suit for men around 100 EUR", false);
        order.verify(persistenceService).recordSearchOutcome(second.getId(), secondOutcome);
        order.verify(persistenceService).finishExecution(conversationId);
    }

    @Test
    void run_sweeperAlreadyFailedTheConversation_stopsWithoutRunningOrFinishing() {
        when(clock.instant()).thenReturn(START);
        stubPending(search(0, "a linen dress", true));
        when(persistenceService.touchExecuting(conversationId)).thenReturn(false);

        executor.run(conversationId);

        verify(generationService, never()).execute(any(), any(), anyBoolean());
        verify(persistenceService, never()).finishExecution(any());
    }

    @Test
    void run_deadlinePassesBeforeTheSecondSearch_skipsItAndFinishes() {
        when(clock.instant()).thenReturn(START, START, START.plus(Duration.ofMinutes(11)));
        StylistSearch first = search(0, "a linen dress", true);
        StylistSearch second = search(1, "a floral dress", true);
        stubPending(first, second);
        when(persistenceService.touchExecuting(conversationId)).thenReturn(true);
        when(generationService.execute(userId, "a linen dress", true)).thenReturn(outcome(AiPromptQueryStatus.SUCCESS));

        executor.run(conversationId);

        verify(generationService, never()).execute(userId, "a floral dress", true);
        verify(persistenceService, never()).recordSearchOutcome(eq(second.getId()), any());
        verify(persistenceService).finishExecution(conversationId);
    }

    @Test
    void run_noPendingSearches_justFinishes() {
        when(clock.instant()).thenReturn(START);
        stubPending();

        executor.run(conversationId);

        verify(generationService, never()).execute(any(), any(), anyBoolean());
        verify(persistenceService).finishExecution(conversationId);
    }

    private void stubPending(StylistSearch... searches) {
        when(searchRepository.findByConversationIdAndStatusOrderByPositionAsc(conversationId, StylistSearchStatus.PENDING))
                .thenReturn(List.of(searches));
    }

    private StylistSearch search(int position, String prompt, boolean matchProfile) {
        return StylistSearch.builder().id(UUID.randomUUID()).position(position).title("Look " + position)
                .prompt(prompt).matchProfile(matchProfile).status(StylistSearchStatus.PENDING).build();
    }

    private PromptQueryOutcome outcome(AiPromptQueryStatus status) {
        return new PromptQueryOutcome(UUID.randomUUID(), status, null, List.of());
    }
}
