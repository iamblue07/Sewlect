package com.sewlect.catalog.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.domain.ProductEnrichmentResult;
import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.common.logging.enums.ExternalCallOutcome;
import com.sewlect.common.logging.support.ExternalCallLogger;
import com.sewlect.common.taxonomy.entity.StyleTag;
import com.sewlect.common.taxonomy.repository.StyleTagRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "ollama", matchIfMissing = true)
public class OllamaEnrichmentService implements EnrichmentService {

    private static final String PROVIDER = "ollama-local";
    private static final String PROVIDER_DATASET_CDN = "dataset-cdn";
    private static final String OPERATION_ENRICH = "enrich-product";
    private static final String OPERATION_DOWNLOAD_IMAGE = "download-product-image";

    private final HttpClient httpClient;

    private final OllamaChatModel ollamaChatModel;
    private final StyleTagRepository styleTagRepository;
    private final ExternalCallLogger externalCallLogger;

    @Override
    public ProductEnrichmentResult enrich(Product product) {
        byte[] imageBytes = downloadImage(product.getImageUrl());
        String allowedStyleTags = styleTagRepository.findAll().stream()
                .map(StyleTag::getName)
                .collect(Collectors.joining(", "));

        long startedAt = System.nanoTime();
        ProductEnrichmentResult result;
        try {
            result = ChatClient.create(ollamaChatModel).prompt()
                    .options(OllamaChatOptions.builder().disableThinking())
                    .user(user -> user
                            .text(buildPrompt(product, allowedStyleTags))
                            .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageBytes)))
                    .call()
                    .entity(ProductEnrichmentResult.class);
        } catch (RuntimeException e) {
            externalCallLogger.logCall(PROVIDER, OPERATION_ENRICH, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException(
                    "Ollama enrichment call failed for product " + product.getId() + ": " + e.getMessage());
        }

        externalCallLogger.logCall(PROVIDER, OPERATION_ENRICH, ExternalCallLogger.elapsedMillisSince(startedAt), ExternalCallOutcome.SUCCESS);
        return result;
    }

    private String buildPrompt(Product product, String allowedStyleTags) {
        return """
                You are enriching a fashion catalog product listing based on its image.
                Product: %s (%s / %s / %s)
                Respond with:
                - fit, silhouette, pattern, materialGuess, formality: short descriptive terms
                - occasion: a single value, e.g. "work", "gym", "beach"
                - primaryColor, secondaryColor: dominant colors visible (secondaryColor may be omitted if there's no distinct second color)
                - layeringRole: exactly one of "base", "mid", "outer"
                - description: a 4-5 sentence description, to be used as embedding source text
                - basePrice: a plausible EUR price for this item, as a plain number
                - styleTagNames: 1-3 tags that best fit this item, chosen only from: %s
                """.formatted(product.getProductDisplayName(), product.getMasterCategory(),
                product.getSubCategory(), product.getArticleType(), allowedStyleTags);
    }

    private byte[] downloadImage(String imageUrl) {
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                externalCallLogger.logCall(PROVIDER_DATASET_CDN, OPERATION_DOWNLOAD_IMAGE, ExternalCallLogger.elapsedMillisSince(startedAt),
                        ExternalCallOutcome.PERMANENT_FAILURE);
                throw new ExternalServiceException(
                        "Failed to download product image, HTTP " + response.statusCode() + ": " + imageUrl);
            }

            externalCallLogger.logCall(PROVIDER_DATASET_CDN, OPERATION_DOWNLOAD_IMAGE, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.SUCCESS);
            return response.body();
        } catch (IOException e) {
            externalCallLogger.logCall(PROVIDER_DATASET_CDN, OPERATION_DOWNLOAD_IMAGE, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.RETRYABLE_FAILURE);
            throw new ExternalServiceException("Failed to download product image " + imageUrl + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            externalCallLogger.logCall(PROVIDER_DATASET_CDN, OPERATION_DOWNLOAD_IMAGE, ExternalCallLogger.elapsedMillisSince(startedAt),
                    ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ExternalServiceException("Interrupted while downloading product image: " + imageUrl);
        }
    }
}