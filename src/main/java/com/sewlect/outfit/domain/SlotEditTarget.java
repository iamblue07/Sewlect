package com.sewlect.outfit.domain;

import com.sewlect.catalog.entity.Product;
import com.sewlect.common.taxonomy.enums.GarmentRole;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SlotEditTarget(
        UUID outfitId,
        GarmentRole role,
        Product currentProduct,
        List<Product> otherProducts,
        Set<String> eligibleGenders
) {

    public boolean hasCurrent() {
        return currentProduct != null;
    }

    public BigDecimal outgoingPrice() {
        return hasCurrent() ? currentProduct.getBasePrice() : BigDecimal.ZERO;
    }

    public List<float[]> otherEmbeddings() {
        return otherProducts.stream().map(Product::getTextEmbedding).toList();
    }
}
