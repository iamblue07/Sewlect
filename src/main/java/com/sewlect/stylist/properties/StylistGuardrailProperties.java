package com.sewlect.stylist.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "stylist.guardrails")
public record StylistGuardrailProperties(
        @Positive int maxReplyLength,
        @Positive int maxTitleLength,
        @Positive int maxSummaryLength,
        @Positive int maxSearchPromptLength,
        @Positive int strikeLimit,
        @NotNull Duration strikeWindow,
        @NotNull Duration lockoutDuration,
        @Positive int dailyMessageLimit,
        @Positive int dailyConversationLimit,
        @NotBlank String refusalMessage
) {
}
