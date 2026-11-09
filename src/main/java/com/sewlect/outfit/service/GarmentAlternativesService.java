package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.common.ai.util.EmbeddingVectorCentroid;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.properties.OutfitSwapProperties;
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
@EnableConfigurationProperties(OutfitSwapProperties.class)
public class GarmentAlternativesService {

    private final ProductSearchService productSearchService;
    private final SlotPriceCeilingResolver slotPriceCeilingResolver;
    private final AlternativeCandidateRanker alternativeCandidateRanker;
    private final OutfitSwapProperties swapProperties;

    public List<AlternativeCandidateResponse> listAlternatives(SlotEditTarget target, UUID userId) {
        BigDecimal priceCeiling = slotPriceCeilingResolver.resolve(target, userId);
        Vector reference = Vector.of(EmbeddingVectorCentroid.compute(target.otherEmbeddings()));
        return alternativeCandidateRanker.rank(target.otherProducts(), findCandidates(target, priceCeiling, reference));
    }

    private List<Product> findCandidates(SlotEditTarget target, BigDecimal priceCeiling, Vector reference) {
        int limit = swapProperties.alternativeLimit();
        if (target.hasCurrent()) {
            Product current = target.currentProduct();
            return productSearchService.findNearestOfArticleType(
                    current.getArticleType(), target.eligibleGenders(), priceCeiling, current.getId(), reference, limit);
        }
        return productSearchService.findNearest(
                target.role(), target.eligibleGenders(), priceCeiling, reference, limit);
    }
}
