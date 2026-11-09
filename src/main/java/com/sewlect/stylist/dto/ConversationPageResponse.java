package com.sewlect.stylist.dto;

import java.util.List;

public record ConversationPageResponse(
        List<ConversationSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
