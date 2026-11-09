package com.sewlect.stylist.support;

import com.sewlect.stylist.domain.AssistantTurn;
import com.sewlect.stylist.domain.SearchProposal;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.AssistantTurnState;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class StylistTurnRenderer {

    private final SearchProposalCodec searchProposalCodec;

    public String renderHistoryMessage(StylistMessage message) {
        if (message.getProposal() == null) {
            return message.getContent();
        }
        SearchProposal proposal = searchProposalCodec.decode(message.getProposal());
        return new StringBuilder(message.getContent())
                .append("\n\n[Summary shown to the user: \"").append(proposal.title()).append("\" - ")
                .append(proposal.summary())
                .append("; matchProfile: ").append(proposal.matchProfile())
                .append("; search prompt, hidden from the user: ").append(proposal.prompt()).append("]")
                .toString();
    }

    public String renderForSafetyCheck(AssistantTurn turn) {
        StringBuilder rendered = new StringBuilder(turn.reply() == null ? "" : turn.reply());
        if (turn.state() == AssistantTurnState.PROPOSING) {
            rendered.append("\n").append(turn.title())
                    .append("\n").append(turn.summary())
                    .append("\n").append(turn.prompt());
        }
        return rendered.toString();
    }
}
