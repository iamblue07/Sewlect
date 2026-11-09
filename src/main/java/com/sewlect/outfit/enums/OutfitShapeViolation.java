package com.sewlect.outfit.enums;

public enum OutfitShapeViolation {
    DUPLICATE_ROLE("An outfit may hold at most one item per garment role"),
    INVALID_CORE("An outfit needs either a top and a bottom together, or a full-body garment alone"),
    MISSING_FOOTWEAR("An outfit must include footwear");

    private final String description;

    OutfitShapeViolation(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
