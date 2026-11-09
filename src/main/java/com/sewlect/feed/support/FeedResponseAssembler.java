package com.sewlect.feed.support;

import com.sewlect.feed.dto.CompatibilityBreakdownResponse;
import com.sewlect.feed.dto.FeedItemResponse;
import com.sewlect.feed.dto.FeedOutfitItemResponse;
import com.sewlect.feed.entity.FeedEntry;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@AllArgsConstructor
public class FeedResponseAssembler {

    private final OutfitResponseAssembler outfitResponseAssembler;

    public List<FeedItemResponse> toResponses(List<FeedEntry> entries, UUID viewerId) {
        List<Outfit> outfits = entries.stream().map(FeedEntry::getOutfit).toList();
        List<OutfitResponse> outfitResponses = outfitResponseAssembler.toResponses(outfits, viewerId);

        List<FeedItemResponse> responses = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            responses.add(toResponse(entries.get(i), outfitResponses.get(i)));
        }
        return responses;
    }

    private FeedItemResponse toResponse(FeedEntry entry, OutfitResponse outfit) {
        List<FeedOutfitItemResponse> items = outfit.items().stream()
                .map(this::toItemResponse)
                .toList();
        return new FeedItemResponse(outfit.outfitId(), toBreakdownResponse(outfit.compatibilityBreakdown()),
                entry.getRankScore(), outfit.totalPrice(), items, outfit.engagement());
    }

    private CompatibilityBreakdownResponse toBreakdownResponse(CompatibilityScoreBreakdown breakdown) {
        return new CompatibilityBreakdownResponse(
                breakdown.colorScore(),
                breakdown.layeringScore(),
                breakdown.structuredScore(),
                breakdown.embeddingScore(),
                breakdown.finalScore());
    }

    private FeedOutfitItemResponse toItemResponse(OutfitItemView view) {
        return new FeedOutfitItemResponse(
                view.itemId(), view.productId(), view.productDisplayName(), view.imageUrl(), view.basePrice(), view.slot(),
                view.removable());
    }
}
