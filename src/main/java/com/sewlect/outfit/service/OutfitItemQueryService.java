package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.entity.OutfitItem;
import com.sewlect.outfit.repository.OutfitItemRepository;
import com.sewlect.outfit.repository.OutfitRepository;
import com.sewlect.outfit.support.OutfitShapeValidator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@AllArgsConstructor
public class OutfitItemQueryService {

    private static final List<GarmentRole> TRY_ON_ORDER = List.of(
            GarmentRole.FULL_BODY,
            GarmentRole.BOTTOM,
            GarmentRole.TOP,
            GarmentRole.OUTERWEAR,
            GarmentRole.FOOTWEAR,
            GarmentRole.ACCESSORY);

    private final OutfitItemRepository outfitItemRepository;
    private final OutfitRepository outfitRepository;
    private final OutfitShapeValidator outfitShapeValidator;

    public Map<UUID, List<UUID>> findProductIdsForOutfits(List<UUID> outfitIds) {
        Map<UUID, List<UUID>> productIdsByOutfitId = new HashMap<>();
        for (UUID outfitId : outfitIds) {
            productIdsByOutfitId.put(outfitId, new ArrayList<>());
        }
        for (OutfitItemRepository.OutfitProductId row : outfitItemRepository.findProductIdsByOutfitIdIn(outfitIds)) {
            productIdsByOutfitId.get(row.getOutfitId()).add(row.getProductId());
        }
        return productIdsByOutfitId;
    }

    public List<OutfitItemView> findItemViews(UUID outfitId) {
        return outfitItemRepository.findByOutfitId(outfitId).stream()
                .map(item -> new OutfitItemView(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getProductDisplayName(),
                        item.getProduct().getImageUrl(),
                        item.getProduct().getBasePrice(),
                        item.getSlot(),
                        outfitShapeValidator.isOptional(item.getSlot())))
                .toList();
    }

    public Map<UUID, List<OutfitItemView>> findItemViewsForOutfits(List<UUID> outfitIds) {
        Map<UUID, List<OutfitItemView>> viewsByOutfitId = new HashMap<>();
        for (UUID outfitId : outfitIds) {
            viewsByOutfitId.put(outfitId, new ArrayList<>());
        }
        for (OutfitItem item : outfitItemRepository.findByOutfitIdIn(outfitIds)) {
            viewsByOutfitId.get(item.getOutfit().getId()).add(new OutfitItemView(
                    item.getId(),
                    item.getProduct().getId(),
                    item.getProduct().getProductDisplayName(),
                    item.getProduct().getImageUrl(),
                    item.getProduct().getBasePrice(),
                    item.getSlot(),
                    outfitShapeValidator.isOptional(item.getSlot())));
        }
        return viewsByOutfitId;
    }

    public BigDecimal sumBasePrice(UUID outfitId) {
        return outfitItemRepository.sumBasePriceByOutfitId(outfitId);
    }

    public Map<UUID, BigDecimal> sumBasePriceForOutfits(List<UUID> outfitIds) {
        Map<UUID, BigDecimal> totalsByOutfitId = new HashMap<>();
        for (UUID outfitId : outfitIds) {
            totalsByOutfitId.put(outfitId, BigDecimal.ZERO);
        }
        for (OutfitItemRepository.OutfitBasePriceTotal total : outfitItemRepository.sumBasePriceByOutfitIdIn(outfitIds)) {
            totalsByOutfitId.put(total.getOutfitId(), total.getTotalBasePrice());
        }
        return totalsByOutfitId;
    }

    public List<OutfitItem> findItems(UUID outfitId) {
        outfitRepository.findById(outfitId)
                .orElseThrow(() -> new ResourceNotFoundException("Outfit not found: " + outfitId));

        return outfitItemRepository.findByOutfitId(outfitId);
    }

    public List<Product> findProductsForTryon(UUID outfitId) {
        outfitRepository.findById(outfitId)
                .orElseThrow(() -> new ResourceNotFoundException("Outfit not found: " + outfitId));

        return outfitItemRepository.findByOutfitId(outfitId).stream()
                .sorted(Comparator.comparingInt(item -> TRY_ON_ORDER.indexOf(item.getSlot())))
                .map(OutfitItem::getProduct)
                .toList();
    }

    public Outfit getReference(UUID outfitId) {
        return outfitRepository.getReferenceById(outfitId);
    }

    public Outfit getById(UUID outfitId) {
        return outfitRepository.findById(outfitId)
                .orElseThrow(() -> new ResourceNotFoundException("Outfit not found: " + outfitId));
    }
}