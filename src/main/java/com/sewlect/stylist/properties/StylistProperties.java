package com.sewlect.stylist.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "stylist")
public record StylistProperties(
        @Positive int maxUserTurns,
        @Positive int maxMessageLength,
        @Positive int messageRateLimitPerHour,
        @NotNull Duration executionTimeout,
        @NotNull Duration staleExecutionThreshold,
        @Positive long staleSweepIntervalMs,
        @Positive int defaultPageSize,
        @Positive int maxPageSize
) {

    public StylistProperties {
        if (executionTimeout != null && staleExecutionThreshold != null
                && staleExecutionThreshold.compareTo(executionTimeout) <= 0) {
            throw new IllegalArgumentException(
                    "stylist.stale-execution-threshold must be strictly greater than stylist.execution-timeout");
        }
    }
}
