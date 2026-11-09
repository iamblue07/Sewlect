package com.sewlect.outfit.domain;

import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.AiPromptQueryStatus;

import java.util.List;
import java.util.UUID;

public record PromptQueryOutcome(
        UUID queryId,
        AiPromptQueryStatus status,
        String errorMessage,
        List<Outfit> outfits
) {
}
