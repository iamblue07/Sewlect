package com.sewlect.stylist.dto;

import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.stylist.enums.StylistSearchStatus;

import java.util.List;
import java.util.UUID;

public record SearchResultResponse(
        UUID searchId,
        int position,
        String title,
        boolean matchProfile,
        StylistSearchStatus status,
        String errorMessage,
        UUID queryId,
        List<OutfitResponse> outfits
) {
}
