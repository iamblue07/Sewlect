package com.sewlect.outfit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromptGenerationRequest(
        @NotBlank
        @Size(max = 1000)
        String prompt,
        Boolean matchProfile
) {
}