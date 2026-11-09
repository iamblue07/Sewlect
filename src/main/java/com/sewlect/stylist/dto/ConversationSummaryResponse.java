package com.sewlect.stylist.dto;

import com.sewlect.stylist.enums.ConversationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationSummaryResponse(
        UUID conversationId,
        String title,
        ConversationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
