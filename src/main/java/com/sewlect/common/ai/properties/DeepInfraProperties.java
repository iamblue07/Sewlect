package com.sewlect.common.ai.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "deepinfra")
public record DeepInfraProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String embeddingModel,
        @NotBlank String chatModel,
        @NotBlank String guardModel,
        @NotBlank @Pattern(regexp = "low|medium|high") String reasoningEffort,
        @NotNull Duration timeout,
        @NotNull Duration guardTimeout,
        @PositiveOrZero int maxRetries
) {
}
