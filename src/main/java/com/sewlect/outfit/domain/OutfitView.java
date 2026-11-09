package com.sewlect.outfit.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OutfitView(
        UUID outfitId,
        CompatibilityScoreBreakdown compatibilityBreakdown,
        BigDecimal totalPrice,
        List<OutfitItemView> items
) {
}
