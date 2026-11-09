package com.sewlect.stylist.dto;

import jakarta.validation.constraints.NotBlank;

public record StartConversationRequest(
        @NotBlank
        String message
) {
}
