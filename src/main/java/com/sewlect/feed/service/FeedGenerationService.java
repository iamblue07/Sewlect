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
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.service.UserProfileQueryService;
import com.sewlect.identity.service.UserStylePreferenceQueryService;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitCandidateGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
@EnableConfigurationProperties(FeedProperties.class)
public class FeedGenerationService {

    private static final String RESET_OPERATION_KEY = "feed-reset";

    private final UserProfileQueryService userProfileQueryService;
    private final UserStylePreferenceQueryService userStylePreferenceQueryService;
    private final OutfitCandidateGenerator outfitCandidateGenerator;
    private final FeedEntryRepository feedEntryRepository;
    private final FeedEntryPersistenceService feedEntryPersistenceService;
    private final FeedRankingService feedRankingService;
    private final FeedCursorCodec feedCursorCodec;
    private final FeedRefillGuard feedRefillGuard;

    @Qualifier("feedRefillExecutor")
    private final AsyncTaskExecutor feedRefillExecutor;

    private final FeedProperties feedProperties;
    private final Clock clock;
    private final RateLimiter rateLimiter;

    public void refillFor(UUID userId) {
        UserProfile profile = userProfileQueryService.getById(userId);
        List<Outfit> candidates = outfitCandidateGenerator.generate(profile);
        Set<UUID> preferredStyleTagIds = userStylePreferenceQueryService.findPreferredStyleTagIds(userId);
        List<Outfit> unqueued = excludeAlreadyQueued(userId, candidates);
        Map<UUID, BigDecimal> rankScores = feedRankingService.rankScores(profile, unqueued, preferredStyleTagIds);

        int saved = 0;
        for (Outfit outfit : unqueued) {
            if (feedEntryPersistenceService.saveIfAbsent(profile.getUser(), outfit, rankScores.get(outfit.getId()))) {
                saved++;
            }
        }

        log.debug("Feed refill for user {}: {} candidates generated, {} new entries saved",
                userId, candidates.size(), saved);
    }

    public void reset(UUID userId) {
        if (!rateLimiter.tryConsume(userId.toString(), RESET_OPERATION_KEY,
                feedProperties.resetRateLimitPerHour(), Duration.ofHours(1))) {
            throw new RateLimitExceededException("Feed reset limit exceeded - try again later");
        }
        int deleted = feedEntryPersistenceService.deleteUnseen(userId);
        log.debug("Feed reset for user {}: {} unseen entries deleted", userId, deleted);
    }

    public FeedPage getPage(UUID userId, String cursor, int pageSize) {
        if (feedEntryRepository.countByUserIdAndShownAtIsNull(userId) == 0) {
            refillFor(userId);
        }

        Limit limit = Limit.of(pageSize);
        List<FeedEntry> page = cursor == null
                ? feedEntryRepository.findByUserIdAndShownAtIsNullOrderByRankScoreDescIdDesc(userId, limit)
                : fetchNextPage(userId, cursor, limit);

        if (!page.isEmpty()) {
            List<UUID> pageIds = page.stream().map(FeedEntry::getId).toList();
            feedEntryPersistenceService.markShown(pageIds, LocalDateTime.now(clock));
        }

        triggerRefillIfNeeded(userId);

        String nextCursor = page.size() < pageSize ? null : encodeCursor(page.get(page.size() - 1));
        return new FeedPage(page, nextCursor);
    }

    private List<FeedEntry> fetchNextPage(UUID userId, String cursor, Limit limit) {
        FeedCursor decoded = feedCursorCodec.decode(cursor);
        return feedEntryRepository.findNextPage(userId, decoded.rankScore(), decoded.id(), limit);
    }

    private void triggerRefillIfNeeded(UUID userId) {
        long remaining = feedEntryRepository.countByUserIdAndShownAtIsNull(userId);
        if (remaining >= feedProperties.refillThreshold()) {
            return;
        }
        if (!feedRefillGuard.tryClaim(userId)) {
            return;
        }
        feedRefillExecutor.submit(() -> {
            try {
                refillFor(userId);
            } catch (RuntimeException e) {
                log.error("Background feed refill failed for user {}", userId, e);
            } finally {
                feedRefillGuard.release(userId);
            }
        });
    }

    private String encodeCursor(FeedEntry lastEntry) {
        return feedCursorCodec.encode(new FeedCursor(lastEntry.getRankScore(), lastEntry.getId()));
    }

    private List<Outfit> excludeAlreadyQueued(UUID userId, List<Outfit> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<UUID> candidateIds = candidates.stream().map(Outfit::getId).toList();
        Set<UUID> queuedIds = new HashSet<>(feedEntryRepository.findOutfitIdsByUserIdAndOutfitIdIn(userId, candidateIds));

        Map<UUID, Outfit> unqueued = new LinkedHashMap<>();
        for (Outfit outfit : candidates) {
            if (!queuedIds.contains(outfit.getId())) {
                unqueued.putIfAbsent(outfit.getId(), outfit);
            }
        }
        return List.copyOf(unqueued.values());
    }
}
