package com.sewlect.outfit.support;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.entity.OutfitItem;
import com.sewlect.outfit.service.OutfitItemQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
public class SlotEditTargetResolver {

    private final OutfitItemQueryService outfitItemQueryService;
    private final OutfitGenderFilterResolver genderFilterResolver;
    private final OutfitShapeValidator shapeValidator;

    public SlotEditTarget forItem(UUID outfitId, UUID itemId) {
        List<OutfitItem> items = outfitItemQueryService.findItems(outfitId);

        OutfitItem targetItem = items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Outfit item " + itemId + " not found in outfit " + outfitId));

        List<Product> otherProducts = items.stream()
                .filter(item -> !item.getId().equals(itemId))
                .map(OutfitItem::getProduct)
                .toList();

        Product current = targetItem.getProduct();
        return new SlotEditTarget(outfitId, targetItem.getSlot(), current, otherProducts,
                genderFilterResolver.compatibleGenders(current.getGender()));
    }

    public SlotEditTarget forEmptySlot(UUID outfitId, GarmentRole role) {
        if (!shapeValidator.isOptional(role)) {
            throw new BadRequestException("Only an optional slot " + shapeValidator.optionalRoles()
                    + " can be filled by adding, got " + role);
        }

        List<OutfitItem> items = outfitItemQueryService.findItems(outfitId);

        if (items.stream().anyMatch(item -> item.getSlot() == role)) {
            throw new ConflictException("Outfit " + outfitId + " already has an item in slot " + role + "; swap it instead");
        }

        List<Product> otherProducts = items.stream().map(OutfitItem::getProduct).toList();
        String coreGender = findCoreProduct(items).map(Product::getGender).orElse(null);

        return new SlotEditTarget(outfitId, role, null, otherProducts,
                genderFilterResolver.compatibleGenders(coreGender));
    }

    private Optional<Product> findCoreProduct(List<OutfitItem> items) {
        Optional<Product> fullBody = productInSlot(items, GarmentRole.FULL_BODY);
        return fullBody.isPresent() ? fullBody : productInSlot(items, GarmentRole.TOP);
    }

    private Optional<Product> productInSlot(List<OutfitItem> items, GarmentRole slot) {
        return items.stream()
                .filter(item -> item.getSlot() == slot)
                .map(OutfitItem::getProduct)
                .findFirst();
    }
}
