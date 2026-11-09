package com.sewlect.stylist.dto;

import com.sewlect.stylist.enums.ConversationStatus;

import java.util.UUID;

public record TurnResponse(
        UUID conversationId,
        ConversationStatus status,
        int userTurnsRemaining,
        MessageResponse userMessage,
        MessageResponse assistantMessage
) {
}
