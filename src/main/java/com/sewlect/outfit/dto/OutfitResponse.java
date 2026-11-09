package com.sewlect.outfit.dto;

import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.domain.OutfitItemView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OutfitResponse(
        UUID outfitId,
        CompatibilityScoreBreakdown compatibilityBreakdown,
        BigDecimal totalPrice,
        List<OutfitItemView> items,
        OutfitEngagement engagement
) {
}