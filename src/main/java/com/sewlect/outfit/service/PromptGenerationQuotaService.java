package com.sewlect.outfit.service;

import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.support.PromptRateLimitResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PromptGenerationQuotaService {

    public static final String GENERATION_OPERATION_KEY = "outfit-prompt-generation";
    public static final String REFINEMENT_OPERATION_KEY = "outfit-prompt-refinement";

    private static final Duration WINDOW = Duration.ofHours(1);

    private final RateLimiter rateLimiter;
    private final PromptRateLimitResolver promptRateLimitResolver;
    private final UserReferenceQueryService userReferenceQueryService;

    public void consumeGeneration(UUID userId, int count) {
        consume(userId, GENERATION_OPERATION_KEY, count);
    }

    public void consumeRefinement(UUID userId) {
        consume(userId, REFINEMENT_OPERATION_KEY, 1);
    }

    private void consume(UUID userId, String operationKey, int permits) {
        User user = userReferenceQueryService.getById(userId);
        int limit = promptRateLimitResolver.resolveLimit(user);
        if (!rateLimiter.tryConsume(userId.toString(), operationKey, limit, WINDOW, permits)) {
            throw new RateLimitExceededException("Rate limit exceeded for this operation - try again later");
        }
    }
}
