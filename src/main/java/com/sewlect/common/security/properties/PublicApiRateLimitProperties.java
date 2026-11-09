package com.sewlect.common.security.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "public-api.rate-limit")
public record PublicApiRateLimitProperties(
        @Positive int perIpLimit,
        @NotNull Duration window
) {
}
