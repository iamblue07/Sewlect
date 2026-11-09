package com.sewlect.stylist.dto;

import com.sewlect.stylist.enums.MessageRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageResponse(
        UUID messageId,
        int sequence,
        MessageRole role,
        String content,
        ProposalResponse proposal,
        LocalDateTime createdAt
) {
}
