package com.sewlect.catalog.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "catalog.vocabulary-cache")
public record CatalogVocabularyCacheProperties(
        @NotNull Duration ttl
) {
}
