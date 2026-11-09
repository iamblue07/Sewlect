package com.sewlect.outfit.service;

import com.sewlect.catalog.entity.Product;
import com.sewlect.catalog.service.ProductSearchService;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.OutfitSource;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import com.sewlect.outfit.support.OutfitShapeValidator;
import com.sewlect.outfit.support.SlotPriceCeilingResolver;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class OutfitEditService {

    private final ProductSearchService productSearchService;
    private final SlotPriceCeilingResolver slotPriceCeilingResolver;
    private final OutfitShapeValidator shapeValidator;
    private final OutfitVariantService outfitVariantService;
    private final OutfitResponseAssembler outfitResponseAssembler;

    public OutfitResponse swap(SlotEditTarget target, UUID productId, UUID userId) {
        return commit(target, productId, userId, OutfitSource.MANUAL_SWAP);
    }

    public OutfitResponse add(SlotEditTarget target, UUID productId, UUID userId) {
        return commit(target, productId, userId, OutfitSource.MANUAL_ADDITION);
    }

    public OutfitResponse remove(SlotEditTarget target, UUID userId) {
        if (!target.hasCurrent() || !shapeValidator.isOptional(target.role())) {
            throw new BadRequestException(
                    "Only an existing item in an optional slot " + shapeValidator.optionalRoles()
                            + " can be removed, got slot " + target.role());
        }
        Outfit outfit = outfitVariantService.createVariant(target.otherProducts(), OutfitSource.MANUAL_REMOVAL);
        return outfitResponseAssembler.toResponse(outfit, userId);
    }

    private OutfitResponse commit(SlotEditTarget target, UUID productId, UUID userId, OutfitSource source) {
        Product candidate = productSearchService.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        validateCandidate(target, candidate, userId);

        List<Product> products = new ArrayList<>(target.otherProducts());
        products.add(candidate);

        Outfit outfit = outfitVariantService.createVariant(products, source);
        return outfitResponseAssembler.toResponse(outfit, userId);
    }

    private void validateCandidate(SlotEditTarget target, Product candidate, UUID userId) {
        if (candidate.getGarmentRole() != target.role()) {
            throw new BadRequestException("Product " + candidate.getId() + " has garment role "
                    + candidate.getGarmentRole() + ", but slot " + target.role() + " was targeted");
        }
        if (!target.eligibleGenders().contains(candidate.getGender())) {
            throw new BadRequestException(
                    "Product " + candidate.getId() + " is not eligible for this outfit's gender pool");
        }
        if (target.hasCurrent() && candidate.getId().equals(target.currentProduct().getId())) {
            throw new BadRequestException("Product " + candidate.getId() + " is already in this slot");
        }
        if (candidate.getTextEmbedding() == null || candidate.getPrimaryColor() == null) {
            throw new BadRequestException("Product " + candidate.getId() + " is not enriched and cannot be scored");
        }

        if (candidate.getBasePrice().compareTo(slotPriceCeilingResolver.resolve(target, userId)) > 0) {
            throw new BadRequestException(
                    "Putting product " + candidate.getId() + " in this slot would exceed the outfit's budget ceiling");
        }
    }
}
