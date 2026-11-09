package com.sewlect.outfit.dto;

import com.sewlect.common.taxonomy.enums.GarmentRole;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicOutfitItemResponse(
        UUID productId,
        String productDisplayName,
        String imageUrl,
        BigDecimal basePrice,
        GarmentRole slot
) {
}
