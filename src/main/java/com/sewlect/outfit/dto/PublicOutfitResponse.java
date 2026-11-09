package com.sewlect.outfit.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicOutfitResponse(
        UUID outfitId,
        BigDecimal totalPrice,
        long likeCount,
        List<PublicOutfitItemResponse> items
) {
}
