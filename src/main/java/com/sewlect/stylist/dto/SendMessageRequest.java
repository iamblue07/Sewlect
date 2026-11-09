package com.sewlect.stylist.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank
        String content
) {
}
