package com.sewlect.stylist.support;

import com.sewlect.stylist.domain.AssistantTurn;
import com.sewlect.stylist.enums.AssistantTurnState;
import com.sewlect.stylist.properties.StylistGuardrailProperties;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.stream.Stream;

@Component
@AllArgsConstructor
public class StylistTurnValidator {

    private final StylistGuardrailProperties guardrailProperties;

    public boolean isValid(AssistantTurn turn) {
        if (turn == null || turn.state() == null) {
            return false;
        }
        if (turn.state() != AssistantTurnState.PROPOSING) {
            return withinLimit(turn.reply(), guardrailProperties.maxReplyLength());
        }
        return (turn.reply() == null || turn.reply().length() <= guardrailProperties.maxReplyLength())
                && withinLimit(turn.title(), guardrailProperties.maxTitleLength())
                && withinLimit(turn.summary(), guardrailProperties.maxSummaryLength())
                && withinLimit(turn.prompt(), guardrailProperties.maxSearchPromptLength());
    }

    public boolean leaksCanary(AssistantTurn turn, String canary) {
        if (turn == null) {
            return false;
        }
        String marker = canary.toLowerCase(Locale.ROOT);
        return Stream.of(turn.reply(), turn.title(), turn.summary(), turn.prompt())
                .anyMatch(text -> text != null && text.toLowerCase(Locale.ROOT).contains(marker));
    }

    public AssistantTurn normalize(AssistantTurn turn) {
        if (turn.state() != AssistantTurnState.PROPOSING) {
            return AssistantTurn.withoutProposal(turn.state(), turn.reply());
        }
        return turn;
    }

    public String usableReply(AssistantTurn turn) {
        return turn != null && withinLimit(turn.reply(), guardrailProperties.maxReplyLength()) ? turn.reply() : null;
    }

    private boolean withinLimit(String text, int maxLength) {
        return StringUtils.hasText(text) && text.length() <= maxLength;
    }
}
