package com.sewlect.stylist.dto;

import com.sewlect.stylist.enums.ConversationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID conversationId,
        String title,
        ConversationStatus status,
        int userTurnsRemaining,
        List<MessageResponse> messages,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt
) {
}
