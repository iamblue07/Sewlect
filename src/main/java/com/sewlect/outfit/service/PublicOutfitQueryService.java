package com.sewlect.outfit.service;

import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.domain.OutfitView;
import com.sewlect.outfit.dto.PublicOutfitItemResponse;
import com.sewlect.outfit.dto.PublicOutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.support.OutfitViewAssembler;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PublicOutfitQueryService {

    private final OutfitItemQueryService outfitItemQueryService;
    private final OutfitViewAssembler outfitViewAssembler;
    private final OutfitEngagementReader outfitEngagementReader;

    public PublicOutfitResponse getPublicView(UUID outfitId) {
        Outfit outfit = outfitItemQueryService.getById(outfitId);
        OutfitView view = outfitViewAssembler.toView(outfit);
        long likeCount = outfitEngagementReader.readLikeCounts(List.of(outfitId)).getOrDefault(outfitId, 0L);

        List<PublicOutfitItemResponse> items = view.items().stream()
                .map(this::toPublicItem)
                .toList();
        return new PublicOutfitResponse(view.outfitId(), view.totalPrice(), likeCount, items);
    }

    private PublicOutfitItemResponse toPublicItem(OutfitItemView item) {
        return new PublicOutfitItemResponse(
                item.productId(), item.productDisplayName(), item.imageUrl(), item.basePrice(), item.slot());
    }
}
