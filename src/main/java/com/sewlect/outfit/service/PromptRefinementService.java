package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.properties.OutfitRefinementProperties;
import com.sewlect.outfit.support.AlternativeCandidateRanker;
import com.sewlect.outfit.support.SlotPriceCeilingResolver;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Vector;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(OutfitRefinementProperties.class)
public class PromptRefinementService {

    private final PromptExtractionService promptExtractionService;
    private final PromptQueryEmbeddingService promptQueryEmbeddingService;
    private final ProductSearchService productSearchService;
    private final SlotPriceCeilingResolver slotPriceCeilingResolver;
    private final AlternativeCandidateRanker alternativeCandidateRanker;
    private final OutfitRefinementProperties properties;

    public List<AlternativeCandidateResponse> refine(SlotEditTarget target, UUID userId, String rawPrompt) {
        String slotDescription = promptExtractionService.extractSingleSlot(rawPrompt, target.role());
        BigDecimal priceCeiling = slotPriceCeilingResolver.resolve(target, userId);

        int queryLimit = properties.candidateLimit() + (target.hasCurrent() ? 1 : 0);
        Vector referenceEmbedding = promptQueryEmbeddingService.embed(slotDescription);
        List<Product> results = productSearchService.findNearest(
                target.role(), target.eligibleGenders(), priceCeiling, referenceEmbedding, queryLimit);

        List<Product> eligible = results.stream()
                .filter(candidate -> !target.hasCurrent() || !candidate.getId().equals(target.currentProduct().getId()))
                .limit(properties.candidateLimit())
                .toList();

        return alternativeCandidateRanker.rank(target.otherProducts(), eligible);
    }
}
