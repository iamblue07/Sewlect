package com.sewlect.outfit.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "outfit.refinement")
public record OutfitRefinementProperties(
        @Positive int candidateLimit
) {
}
