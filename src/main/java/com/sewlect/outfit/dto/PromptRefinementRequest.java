package com.sewlect.outfit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromptRefinementRequest(
        @NotBlank
        @Size(max = 1000)
        String prompt
) {
}