package com.sewlect.outfit.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProductSelectionRequest(
        @NotNull
        UUID productId
) {
}
