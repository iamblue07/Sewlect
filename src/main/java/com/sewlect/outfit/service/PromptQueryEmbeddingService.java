package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.ai.util.EmbeddingVectorTruncator;
import com.sewlect.common.ai.config.DeepInfraConfig;
import com.sewlect.common.logging.enums.ExternalCallOutcome;
import com.sewlect.common.logging.support.ExternalCallLogger;
import lombok.AllArgsConstructor;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PromptQueryEmbeddingService {

    private static final String OPERATION_EMBED = "embed-query";

    private final OpenAiEmbeddingModel openAiEmbeddingModel;
    private final ExternalCallLogger externalCallLogger;

    public Vector embed(String text) {
        long startedAt = System.nanoTime();
        float[] rawEmbedding;
        try {
            rawEmbedding = openAiEmbeddingModel.embed(text);
        } catch (RuntimeException e) {
            externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EMBED, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw e;
        }

        externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_EMBED, ExternalCallLogger.elapsedMillisSince(startedAt), ExternalCallOutcome.SUCCESS);
        return Vector.of(EmbeddingVectorTruncator.truncateAndNormalize(rawEmbedding, Product.TEXT_EMBEDDING_DIMENSIONS));
    }
}