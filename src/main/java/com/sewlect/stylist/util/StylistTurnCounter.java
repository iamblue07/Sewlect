package com.sewlect.stylist.util;

import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.MessageRole;

import java.util.List;

public final class StylistTurnCounter {

    private StylistTurnCounter() {
    }

    public static long countUserTurns(List<StylistMessage> messages) {
        return messages.stream().filter(message -> message.getRole() == MessageRole.USER).count();
    }
}
