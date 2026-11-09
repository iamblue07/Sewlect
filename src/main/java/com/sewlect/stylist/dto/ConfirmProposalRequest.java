package com.sewlect.stylist.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmProposalRequest(
        @NotNull
        UUID messageId
) {
}
