package com.sewlect.feed.service;

import com.sewlect.catalog.service.ProductStyleTagQueryService;
import com.sewlect.feed.properties.FeedRankingProperties;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.properties.OutfitGenerationProperties;
import com.sewlect.outfit.service.OutfitItemQueryService;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(FeedRankingProperties.class)
public class FeedRankingService {

    private static final int INTERNAL_SCALE = 10;
    private static final BigDecimal NEUTRAL_SCORE = new BigDecimal("0.5");
    private static final BigDecimal TWO = new BigDecimal("2");

    private final ProductStyleTagQueryService productStyleTagQueryService;
    private final OutfitItemQueryService outfitItemQueryService;
    private final OutfitGenerationProperties generationProperties;
    private final FeedRankingProperties rankingProperties;

    public Map<UUID, BigDecimal> rankScores(UserProfile profile, List<Outfit> outfits, Set<UUID> preferredStyleTagIds) {
        if (outfits.isEmpty()) {
            return Map.of();
        }

        List<UUID> outfitIds = outfits.stream().map(Outfit::getId).toList();
        Map<UUID, Set<UUID>> styleTagIdsByOutfitId = preferredStyleTagIds.isEmpty()
                ? Map.of()
                : findStyleTagIdsByOutfitId(outfitIds);
        Map<UUID, BigDecimal> totalPriceByOutfitId = profile.getAverageBudgetPerOutfit() == null
                ? Map.of()
                : outfitItemQueryService.sumBasePriceForOutfits(outfitIds);

        Map<UUID, BigDecimal> rankScores = new HashMap<>();
        for (Outfit outfit : outfits) {
            BigDecimal styleOverlap = styleOverlap(
                    preferredStyleTagIds, styleTagIdsByOutfitId.getOrDefault(outfit.getId(), Set.of()));
            BigDecimal budgetFit = budgetFit(
                    profile.getAverageBudgetPerOutfit(), totalPriceByOutfitId.get(outfit.getId()));
            rankScores.put(outfit.getId(), rankScore(outfit, styleOverlap, budgetFit));
        }
        return rankScores;
    }

    private BigDecimal rankScore(Outfit outfit, BigDecimal styleOverlap, BigDecimal budgetFit) {
        BigDecimal personalization = rankingProperties.styleWeight().multiply(styleOverlap)
                .add(rankingProperties.budgetWeight().multiply(budgetFit));

        BigDecimal multiplier = BigDecimal.ONE.add(
                rankingProperties.personalizationWeight()
                        .multiply(TWO)
                        .multiply(personalization.subtract(NEUTRAL_SCORE)));

        return outfit.getCompatibilityScore().multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    }

    private Map<UUID, Set<UUID>> findStyleTagIdsByOutfitId(List<UUID> outfitIds) {
        Map<UUID, List<UUID>> productIdsByOutfitId = outfitItemQueryService.findProductIdsForOutfits(outfitIds);
        List<UUID> productIds = productIdsByOutfitId.values().stream().flatMap(List::stream).distinct().toList();
        Map<UUID, Set<UUID>> styleTagIdsByProductId = productStyleTagQueryService.findStyleTagIdsForProducts(productIds);

        Map<UUID, Set<UUID>> styleTagIdsByOutfitId = new HashMap<>();
        productIdsByOutfitId.forEach((outfitId, outfitProductIds) -> {
            Set<UUID> outfitTagIds = new HashSet<>();
            for (UUID productId : outfitProductIds) {
                outfitTagIds.addAll(styleTagIdsByProductId.getOrDefault(productId, Set.of()));
            }
            styleTagIdsByOutfitId.put(outfitId, outfitTagIds);
        });
        return styleTagIdsByOutfitId;
    }

    private BigDecimal styleOverlap(Set<UUID> preferredStyleTagIds, Set<UUID> outfitTagIds) {
        if (preferredStyleTagIds.isEmpty() || outfitTagIds.isEmpty()) {
            return NEUTRAL_SCORE;
        }

        Set<UUID> intersection = new HashSet<>(preferredStyleTagIds);
        intersection.retainAll(outfitTagIds);

        Set<UUID> union = new HashSet<>(preferredStyleTagIds);
        union.addAll(outfitTagIds);

        return BigDecimal.valueOf(intersection.size())
                .divide(BigDecimal.valueOf(union.size()), INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal budgetFit(BigDecimal budget, BigDecimal totalPrice) {
        if (budget == null) {
            return NEUTRAL_SCORE;
        }

        if (totalPrice.compareTo(budget) <= 0) {
            return BigDecimal.ONE;
        }

        BigDecimal toleranceAmount = budget.multiply(generationProperties.budgetTolerance());
        BigDecimal toleranceEdge = budget.add(toleranceAmount);
        if (totalPrice.compareTo(toleranceEdge) >= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal overage = totalPrice.subtract(budget);
        return BigDecimal.ONE.subtract(overage.divide(toleranceAmount, INTERNAL_SCALE, RoundingMode.HALF_UP));
    }
}
