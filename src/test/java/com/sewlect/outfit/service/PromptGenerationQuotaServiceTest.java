package com.sewlect.outfit.service;

import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.ratelimit.InMemoryRateLimiter;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.enums.Role;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.properties.OutfitPromptProperties;
import com.sewlect.outfit.support.PromptRateLimitResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptGenerationQuotaServiceTest {

    private static final Duration HOUR = Duration.ofHours(1);

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private UserReferenceQueryService userReferenceQueryService;

    private PromptRateLimitResolver rateLimitResolver;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rateLimitResolver = new PromptRateLimitResolver(new OutfitPromptProperties(2, 3, 200, 200, 50));
    }

    @Test
    void consumeGeneration_consumesEverySearchAsOneAtomicRequestOnTheGenerationKey() {
        stubUser(Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any(), anyInt())).thenReturn(true);

        service(rateLimiter).consumeGeneration(userId, 3);

        verify(rateLimiter).tryConsume(userId.toString(), "outfit-prompt-generation", 200, HOUR, 3);
    }

    @Test
    void consumeGeneration_refused_throwsRateLimitExceeded() {
        stubUser(Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service(rateLimiter).consumeGeneration(userId, 2))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void consumeRefinement_consumesOneUnitOnTheRefinementKey() {
        stubUser(Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any(), anyInt())).thenReturn(true);

        service(rateLimiter).consumeRefinement(userId);

        verify(rateLimiter).tryConsume(userId.toString(), "outfit-prompt-refinement", 200, HOUR, 1);
    }

    @Test
    void consumeRefinement_refused_throwsRateLimitExceeded() {
        stubUser(Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service(rateLimiter).consumeRefinement(userId))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void admin_isLimitedByTheResolversUnboundedLimit() {
        stubUser(Role.ADMIN);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any(), anyInt())).thenReturn(true);

        service(rateLimiter).consumeGeneration(userId, 3);

        verify(rateLimiter).tryConsume(userId.toString(), "outfit-prompt-generation", Integer.MAX_VALUE, HOUR, 3);
    }

    @Test
    void sharedBudget_aRefusedMultiSearchRequestLeavesTheBudgetForASmallerOne() {
        stubUser(Role.USER);
        PromptRateLimitResolver tightResolver = new PromptRateLimitResolver(new OutfitPromptProperties(2, 3, 2, 200, 50));
        InMemoryRateLimiter realLimiter = new InMemoryRateLimiter(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        PromptGenerationQuotaService quota =
                new PromptGenerationQuotaService(realLimiter, tightResolver, userReferenceQueryService);

        assertThatThrownBy(() -> quota.consumeGeneration(userId, 3)).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> quota.consumeGeneration(userId, 1)).doesNotThrowAnyException();
        assertThatCode(() -> quota.consumeGeneration(userId, 1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> quota.consumeGeneration(userId, 1)).isInstanceOf(RateLimitExceededException.class);
    }

    private PromptGenerationQuotaService service(RateLimiter limiter) {
        return new PromptGenerationQuotaService(limiter, rateLimitResolver, userReferenceQueryService);
    }

    private void stubUser(Role role) {
        when(userReferenceQueryService.getById(userId)).thenReturn(User.builder().id(userId).role(role).build());
    }
}
