package com.sewlect.identity.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "identity.password-change")
public record PasswordChangeProperties(
        @Positive int rateLimit,
        @NotNull Duration window
) {
}
