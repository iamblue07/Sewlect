package com.sewlect.feed.service;

import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.feed.properties.FeedProperties;
import com.sewlect.feed.dto.FeedCursor;
import com.sewlect.feed.dto.FeedPage;
import com.sewlect.feed.entity.FeedEntry;
import com.sewlect.feed.repository.FeedEntryRepository;
import com.sewlect.feed.support.FeedCursorCodec;
import com.sewlect.feed.support.FeedRefillGuard;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.identity.service.UserStylePreferenceQueryService;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitCandidateGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedGenerationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserProfileQueryService userProfileQueryService;
    @Mock
    private UserStylePreferenceQueryService userStylePreferenceQueryService;
    @Mock
    private OutfitCandidateGenerator outfitCandidateGenerator;
    @Mock
    private FeedEntryRepository feedEntryRepository;
    @Mock
    private FeedEntryPersistenceService feedEntryPersistenceService;
    @Mock
    private FeedRankingService feedRankingService;
    @Mock
    private FeedCursorCodec feedCursorCodec;
    @Mock
    private FeedRefillGuard feedRefillGuard;
    @Mock
    private AsyncTaskExecutor feedRefillExecutor;

    @Mock
    private RateLimiter rateLimiter;

    private final FeedProperties feedProperties = new FeedProperties(20, 30, 300000, 20);

    private FeedGenerationService service;

    private UUID userId;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        service = new FeedGenerationService(
                userProfileQueryService, userStylePreferenceQueryService, outfitCandidateGenerator, feedEntryRepository,
                feedEntryPersistenceService, feedRankingService, feedCursorCodec, feedRefillGuard,
                feedRefillExecutor, feedProperties, FIXED_CLOCK, rateLimiter);

        userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        profile = UserProfile.builder().userId(userId).user(user).averageBudgetPerOutfit(new BigDecimal("100")).build();
        lenient().when(userProfileQueryService.getById(userId)).thenReturn(profile);
    }

    @Test
    void refillFor_allCandidatesNew_savesEveryOne() {
        Outfit outfit1 = outfitWithId();
        Outfit outfit2 = outfitWithId();
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of(outfit1, outfit2));
        when(feedEntryRepository.findOutfitIdsByUserIdAndOutfitIdIn(eq(userId), any())).thenReturn(List.of());
        when(feedRankingService.rankScores(eq(profile), eq(List.of(outfit1, outfit2)), any())).thenReturn(Map.of(
                outfit1.getId(), new BigDecimal("0.5"), outfit2.getId(), new BigDecimal("0.7")));
        when(feedEntryPersistenceService.saveIfAbsent(any(), any(), any())).thenReturn(true);

        service.refillFor(userId);

        verify(feedEntryPersistenceService).saveIfAbsent(profile.getUser(), outfit1, new BigDecimal("0.5"));
        verify(feedEntryPersistenceService).saveIfAbsent(profile.getUser(), outfit2, new BigDecimal("0.7"));
    }

    @Test
    void refillFor_someCandidatesAlreadyInUsersFeed_skipsThemEntirely() {
        Outfit alreadyPresent = outfitWithId();
        Outfit brandNew = outfitWithId();
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of(alreadyPresent, brandNew));
        when(feedEntryRepository.findOutfitIdsByUserIdAndOutfitIdIn(userId,
                List.of(alreadyPresent.getId(), brandNew.getId()))).thenReturn(List.of(alreadyPresent.getId()));
        when(feedRankingService.rankScores(eq(profile), eq(List.of(brandNew)), any()))
                .thenReturn(Map.of(brandNew.getId(), new BigDecimal("0.6")));
        when(feedEntryPersistenceService.saveIfAbsent(any(), any(), any())).thenReturn(true);

        service.refillFor(userId);

        verify(feedEntryPersistenceService, never()).saveIfAbsent(any(), eq(alreadyPresent), any());
        verify(feedEntryPersistenceService).saveIfAbsent(profile.getUser(), brandNew, new BigDecimal("0.6"));
    }

    @Test
    void refillFor_oneCandidateLosesRaceOnSave_othersStillProcessedNoException() {
        Outfit raceLoser = outfitWithId();
        Outfit raceWinner = outfitWithId();
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of(raceLoser, raceWinner));
        when(feedEntryRepository.findOutfitIdsByUserIdAndOutfitIdIn(eq(userId), any())).thenReturn(List.of());
        when(feedRankingService.rankScores(any(), any(), any())).thenReturn(Map.of(
                raceLoser.getId(), new BigDecimal("0.5"), raceWinner.getId(), new BigDecimal("0.5")));
        when(feedEntryPersistenceService.saveIfAbsent(any(), eq(raceLoser), any())).thenReturn(false);
        when(feedEntryPersistenceService.saveIfAbsent(any(), eq(raceWinner), any())).thenReturn(true);

        assertThatCode(() -> service.refillFor(userId)).doesNotThrowAnyException();

        verify(feedEntryPersistenceService).saveIfAbsent(any(), eq(raceLoser), any());
        verify(feedEntryPersistenceService).saveIfAbsent(any(), eq(raceWinner), any());
    }

    @Test
    void refillFor_generatorReturnsNoCandidates_completesCleanlyWithNoWrites() {
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of());

        service.refillFor(userId);

        verify(feedEntryPersistenceService, never()).saveIfAbsent(any(), any(), any());
        verify(feedEntryRepository, never()).findOutfitIdsByUserIdAndOutfitIdIn(any(), any());
    }

    @Test
    void refillFor_generatorReturnsTheSameOutfitTwice_ranksAndSavesItOnce() {
        Outfit reused = outfitWithId();
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of(reused, reused));
        when(feedEntryRepository.findOutfitIdsByUserIdAndOutfitIdIn(eq(userId), any())).thenReturn(List.of());
        when(feedRankingService.rankScores(eq(profile), eq(List.of(reused)), any()))
                .thenReturn(Map.of(reused.getId(), new BigDecimal("0.4")));
        when(feedEntryPersistenceService.saveIfAbsent(any(), any(), any())).thenReturn(true);

        service.refillFor(userId);

        verify(feedEntryPersistenceService, times(1)).saveIfAbsent(profile.getUser(), reused, new BigDecimal("0.4"));
    }

    @Test
    void refillFor_manyCandidates_checksTheExistingFeedAndRanksInOneCallEach() {
        List<Outfit> candidates = List.of(outfitWithId(), outfitWithId(), outfitWithId(), outfitWithId());
        when(outfitCandidateGenerator.generate(profile)).thenReturn(candidates);
        when(feedEntryRepository.findOutfitIdsByUserIdAndOutfitIdIn(eq(userId), any())).thenReturn(List.of());
        when(feedRankingService.rankScores(any(), any(), any())).thenReturn(Map.of());
        when(feedEntryPersistenceService.saveIfAbsent(any(), any(), any())).thenReturn(true);

        service.refillFor(userId);

        verify(feedEntryRepository, times(1)).findOutfitIdsByUserIdAndOutfitIdIn(any(), any());
        verify(feedRankingService, times(1)).rankScores(any(), any(), any());
        verify(feedEntryPersistenceService, times(4)).saveIfAbsent(any(), any(), any());
    }

    @Test
    void getPage_zeroUnseenEntries_triggersSynchronousRefillBeforeReturning() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(0L, 30L);
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of());
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        verify(userProfileQueryService).getById(userId);
        verify(outfitCandidateGenerator).generate(profile);
    }

    @Test
    void getPage_nonZeroUnseenAboveThreshold_neverTriggersAnyRefillAtAll() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        verify(userProfileQueryService, never()).getById(any());
        verify(outfitCandidateGenerator, never()).generate(any());
        verify(feedRefillExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    void getPage_nullCursor_usesFirstPageQuery() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        verify(feedEntryRepository).findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any());
        verify(feedCursorCodec, never()).decode(any());
    }

    @Test
    void getPage_presentCursor_decodesAndUsesKeysetQuery() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        FeedCursor decoded = new FeedCursor(new BigDecimal("0.7500"), UUID.randomUUID());
        when(feedCursorCodec.decode("opaque-cursor")).thenReturn(decoded);
        when(feedEntryRepository.findNextPage(eq(userId), eq(decoded.rankScore()), eq(decoded.id()), any()))
                .thenReturn(List.of());

        service.getPage(userId, "opaque-cursor", 20);

        verify(feedEntryRepository).findNextPage(userId, decoded.rankScore(), decoded.id(), Limit.of(20));
        verify(feedEntryRepository, never()).findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(any(), any());
    }

    @Test
    void getPage_nonEmptyPage_marksEveryReturnedEntryShownWithClockDerivedTimestamp() {
        FeedEntry entry1 = entryWithId();
        FeedEntry entry2 = entryWithId();
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of(entry1, entry2));

        service.getPage(userId, null, 20);

        LocalDateTime expectedNow = LocalDateTime.now(FIXED_CLOCK);
        verify(feedEntryPersistenceService).markShown(List.of(entry1.getId(), entry2.getId()), expectedNow);
    }

    @Test
    void getPage_emptyPage_neverCallsMarkShown() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        verify(feedEntryPersistenceService, never()).markShown(any(), any());
    }

    @Test
    void getPage_fullPage_encodesNextCursorFromLastEntry() {
        FeedEntry last = entryWithId();
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of(last));
        when(feedCursorCodec.encode(new FeedCursor(last.getRankScore(), last.getId()))).thenReturn("encoded-cursor");

        FeedPage page = service.getPage(userId, null, 1);

        assertThat(page.nextCursor()).isEqualTo("encoded-cursor");
    }

    @Test
    void getPage_shortPage_nextCursorIsNullAndEncodeNeverCalled() {
        FeedEntry only = entryWithId();
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of(only));

        FeedPage page = service.getPage(userId, null, 20);

        assertThat(page.nextCursor()).isNull();
        verify(feedCursorCodec, never()).encode(any());
    }

    @Test
    void getPage_emptyPage_nextCursorIsNull() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        FeedPage page = service.getPage(userId, null, 20);

        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void getPage_belowThresholdAndGuardAvailable_submitsBackgroundRefill() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L, 10L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(feedRefillGuard.tryClaim(userId)).thenReturn(true);

        service.getPage(userId, null, 20);

        verify(feedRefillExecutor).submit(any(Runnable.class));
    }

    @Test
    void getPage_aboveThreshold_neverAttemptsToClaimOrSubmit() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());

        service.getPage(userId, null, 20);

        verify(feedRefillGuard, never()).tryClaim(any());
        verify(feedRefillExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    void getPage_belowThresholdButRefillAlreadyInFlight_doesNotSubmitASecondOne() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L, 5L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(feedRefillGuard.tryClaim(userId)).thenReturn(false);

        service.getPage(userId, null, 20);

        verify(feedRefillExecutor, never()).submit(any(Runnable.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void backgroundRefillTask_onSuccess_releasesGuardAfterCompleting() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L, 5L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(feedRefillGuard.tryClaim(userId)).thenReturn(true);
        when(outfitCandidateGenerator.generate(profile)).thenReturn(List.of());
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(feedRefillExecutor.submit(taskCaptor.capture()))
                .thenReturn(mock(Future.class));

        service.getPage(userId, null, 20);
        taskCaptor.getValue().run();

        verify(feedRefillGuard).release(userId);
        verify(outfitCandidateGenerator, times(1)).generate(profile);
    }

    @SuppressWarnings("unchecked")
    @Test
    void backgroundRefillTask_refillThrows_exceptionSwallowedAndGuardStillReleased() {
        when(feedEntryRepository.countByUserIdAndShownAtIsNull(userId)).thenReturn(40L, 5L);
        when(feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(eq(userId), any()))
                .thenReturn(List.of());
        when(feedRefillGuard.tryClaim(userId)).thenReturn(true);
        when(outfitCandidateGenerator.generate(profile)).thenThrow(new IllegalStateException("Ollama is down"));
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(feedRefillExecutor.submit(taskCaptor.capture()))
                .thenReturn(mock(Future.class));

        service.getPage(userId, null, 20);

        assertThatCode(() -> taskCaptor.getValue().run()).doesNotThrowAnyException();
        verify(feedRefillGuard).release(userId);
    }

    private Outfit outfitWithId() {
        return Outfit.builder().id(UUID.randomUUID()).compatibilityScore(new BigDecimal("0.7")).build();
    }

    private FeedEntry entryWithId() {
        return FeedEntry.builder().id(UUID.randomUUID()).rankScore(new BigDecimal("0.6500")).build();
    }

    @Test
    void reset_underTheLimit_consumesTheFeedResetKeyThenDeletesTheUnseenEntries() {
        when(rateLimiter.tryConsume(userId.toString(), "feed-reset", 20, Duration.ofHours(1))).thenReturn(true);

        service.reset(userId);

        InOrder order = inOrder(rateLimiter, feedEntryPersistenceService);
        order.verify(rateLimiter).tryConsume(userId.toString(), "feed-reset", 20, Duration.ofHours(1));
        order.verify(feedEntryPersistenceService).deleteUnseen(userId);
    }

    @Test
    void reset_limitExhausted_throwsRateLimitExceededAndDeletesNothing() {
        when(rateLimiter.tryConsume(userId.toString(), "feed-reset", 20, Duration.ofHours(1))).thenReturn(false);

        assertThatThrownBy(() -> service.reset(userId)).isInstanceOf(RateLimitExceededException.class);

        verify(feedEntryPersistenceService, never()).deleteUnseen(any());
    }

    @Test
    void reset_neverRegeneratesTheFeedItself() {
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);

        service.reset(userId);

        verifyNoInteractions(outfitCandidateGenerator, feedRefillExecutor, feedRefillGuard);
    }
}
