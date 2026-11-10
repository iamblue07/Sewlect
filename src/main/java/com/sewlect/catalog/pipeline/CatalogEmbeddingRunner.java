package com.sewlect.catalog.pipeline;

import com.sewlect.catalog.properties.CatalogEmbeddingProperties;
import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.CatalogEmbeddingService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@AllArgsConstructor
@EnableConfigurationProperties(CatalogEmbeddingProperties.class)
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "ollama", matchIfMissing = true)
public class CatalogEmbeddingRunner implements CommandLineRunner {

    private final CatalogEmbeddingProperties properties;
    private final CatalogEmbeddingService catalogEmbeddingService;

    @Override
    public void run(String... args) {
        if (!properties.batchEnabled()) {
            log.debug("catalog.embedding.batch-enabled is false; skipping embedding batch");
            return;
        }

        log.info("Starting catalog embedding batch");
        Set<UUID> failedIds = new HashSet<>();
        int embeddedCount = 0;

        List<Product> chunk = catalogEmbeddingService.nextChunk(failedIds);
        while (!chunk.isEmpty()) {
            embeddedCount += embedWithFallback(chunk, failedIds);
            log.info("Embedding progress: {} completed, {} failed", embeddedCount, failedIds.size());
            chunk = catalogEmbeddingService.nextChunk(failedIds);
        }

        log.info("Catalog embedding complete: {} embedded, {} failed", embeddedCount, failedIds.size());
    }

    private int embedWithFallback(List<Product> chunk, Set<UUID> failedIds) {
        try {
            catalogEmbeddingService.embedChunk(chunk);
            return chunk.size();
        } catch (Exception e) {
            log.warn("Chunk of {} failed as a batch, retrying item by item: {}", chunk.size(), e.getMessage());
            return embedItemByItem(chunk, failedIds);
        }
    }

    private int embedItemByItem(List<Product> chunk, Set<UUID> failedIds) {
        int succeeded = 0;
        for (Product product : chunk) {
            try {
                catalogEmbeddingService.embedChunk(List.of(product));
                succeeded++;
            } catch (Exception e) {
                log.error("Failed to embed product {}: {}", product.getId(), e.getMessage(), e);
                failedIds.add(product.getId());
            }
        }
        return succeeded;
    }
}