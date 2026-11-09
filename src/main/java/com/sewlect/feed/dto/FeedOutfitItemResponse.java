package com.sewlect.feed.dto;

import com.sewlect.common.taxonomy.enums.GarmentRole;

import java.math.BigDecimal;
import java.util.UUID;

public record FeedOutfitItemResponse(
        UUID itemId,
        UUID productId,
        String productDisplayName,
        String imageUrl,
        BigDecimal basePrice,
        GarmentRole slot,
        boolean removable
) {
}