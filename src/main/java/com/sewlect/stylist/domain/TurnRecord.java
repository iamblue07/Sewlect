package com.sewlect.stylist.domain;

import com.sewlect.stylist.entity.StylistMessage;

public record TurnRecord(
        StylistMessage userMessage,
        StylistMessage assistantMessage
) {
}
