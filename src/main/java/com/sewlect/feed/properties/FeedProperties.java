package com.sewlect.feed.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "feed")
public record FeedProperties(
        @Positive int pageSize,
        @Positive int refillThreshold,
        @Positive long refillLockTtlMs,
        @Positive int resetRateLimitPerHour
) {
}