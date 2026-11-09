package com.sewlect.stylist.domain;

import com.sewlect.stylist.enums.AssistantTurnState;

public record AssistantTurn(
        AssistantTurnState state,
        String reply,
        String title,
        String summary,
        String prompt,
        Boolean matchProfile
) {

    public static AssistantTurn withoutProposal(AssistantTurnState state, String reply) {
        return new AssistantTurn(state, reply, null, null, null, null);
    }

    public SearchProposal toProposal() {
        return new SearchProposal(title, summary, prompt, Boolean.TRUE.equals(matchProfile));
    }
}
