package com.sewlect.outfit.domain;

import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.enums.AiPromptQueryStatus;

import java.util.List;
import java.util.UUID;

public record PromptQueryResult(
        UUID queryId,
        AiPromptQueryStatus status,
        String errorMessage,
        List<OutfitResponse> outfits
) {
}
