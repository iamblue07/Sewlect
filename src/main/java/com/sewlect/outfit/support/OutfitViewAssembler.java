package com.sewlect.outfit.support;

import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.domain.OutfitView;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitItemQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@AllArgsConstructor
public class OutfitViewAssembler {

    private final OutfitItemQueryService outfitItemQueryService;
    private final OutfitViewCache outfitViewCache;

    public OutfitView toView(Outfit outfit) {
        UUID outfitId = outfit.getId();
        OutfitView cached = outfitViewCache.getAll(List.of(outfitId)).get(outfitId);
        if (cached != null) {
            return cached;
        }

        OutfitView assembled = new OutfitView(
                outfitId,
                toBreakdown(outfit),
                outfitItemQueryService.sumBasePrice(outfitId),
                outfitItemQueryService.findItemViews(outfitId));
        outfitViewCache.putAll(Map.of(outfitId, assembled));
        return assembled;
    }

    public List<OutfitView> toViews(List<Outfit> outfits) {
        if (outfits.isEmpty()) {
            return List.of();
        }

        List<UUID> outfitIds = outfits.stream().map(Outfit::getId).toList();
        Map<UUID, OutfitView> cached = outfitViewCache.getAll(outfitIds);

        List<Outfit> misses = outfits.stream()
                .filter(outfit -> !cached.containsKey(outfit.getId()))
                .toList();
        Map<UUID, OutfitView> assembled = assemble(misses);
        outfitViewCache.putAll(assembled);

        return outfits.stream()
                .map(outfit -> cached.containsKey(outfit.getId())
                        ? cached.get(outfit.getId())
                        : assembled.get(outfit.getId()))
                .toList();
    }

    private Map<UUID, OutfitView> assemble(List<Outfit> outfits) {
        if (outfits.isEmpty()) {
            return Map.of();
        }

        List<UUID> outfitIds = outfits.stream().map(Outfit::getId).toList();
        Map<UUID, List<OutfitItemView>> itemViewsByOutfitId = outfitItemQueryService.findItemViewsForOutfits(outfitIds);
        Map<UUID, BigDecimal> totalPriceByOutfitId = outfitItemQueryService.sumBasePriceForOutfits(outfitIds);

        Map<UUID, OutfitView> assembled = new HashMap<>();
        for (Outfit outfit : outfits) {
            assembled.put(outfit.getId(), new OutfitView(
                    outfit.getId(),
                    toBreakdown(outfit),
                    totalPriceByOutfitId.get(outfit.getId()),
                    itemViewsByOutfitId.get(outfit.getId())));
        }
        return assembled;
    }

    private CompatibilityScoreBreakdown toBreakdown(Outfit outfit) {
        return new CompatibilityScoreBreakdown(
                outfit.getColorScore(),
                outfit.getLayeringScore(),
                outfit.getStructuredScore(),
                outfit.getEmbeddingScore(),
                outfit.getCompatibilityScore());
    }
}
