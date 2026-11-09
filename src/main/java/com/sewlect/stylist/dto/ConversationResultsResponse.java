package com.sewlect.stylist.dto;

import com.sewlect.stylist.enums.ConversationStatus;

import java.util.List;
import java.util.UUID;

public record ConversationResultsResponse(
        UUID conversationId,
        ConversationStatus status,
        List<SearchResultResponse> searches
) {
}
