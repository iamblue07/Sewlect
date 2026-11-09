package com.sewlect.outfit.support;

import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.domain.OutfitView;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitEngagementReader;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@AllArgsConstructor
public class OutfitResponseAssembler {

    private final OutfitViewAssembler outfitViewAssembler;
    private final OutfitEngagementReader outfitEngagementReader;

    public OutfitResponse toResponse(Outfit outfit, UUID viewerId) {
        OutfitView view = outfitViewAssembler.toView(outfit);
        Map<UUID, OutfitEngagement> engagement = outfitEngagementReader.read(List.of(view.outfitId()), viewerId);
        return merge(view, engagement);
    }

    public List<OutfitResponse> toResponses(List<Outfit> outfits, UUID viewerId) {
        if (outfits.isEmpty()) {
            return List.of();
        }

        List<OutfitView> views = outfitViewAssembler.toViews(outfits);
        List<UUID> outfitIds = views.stream().map(OutfitView::outfitId).toList();
        Map<UUID, OutfitEngagement> engagement = outfitEngagementReader.read(outfitIds, viewerId);

        return views.stream().map(view -> merge(view, engagement)).toList();
    }

    private OutfitResponse merge(OutfitView view, Map<UUID, OutfitEngagement> engagementByOutfitId) {
        return new OutfitResponse(
                view.outfitId(),
                view.compatibilityBreakdown(),
                view.totalPrice(),
                view.items(),
                engagementByOutfitId.getOrDefault(view.outfitId(), OutfitEngagement.none()));
    }
}
