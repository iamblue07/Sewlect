package com.sewlect.stylist.support;

import com.sewlect.stylist.domain.SearchProposal;
import com.sewlect.stylist.domain.TurnRecord;
import com.sewlect.stylist.dto.ConversationResponse;
import com.sewlect.stylist.dto.ConversationSummaryResponse;
import com.sewlect.stylist.dto.MessageResponse;
import com.sewlect.stylist.dto.ProposalResponse;
import com.sewlect.stylist.dto.TurnResponse;
import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.ConversationStatus;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.util.StylistTurnCounter;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@AllArgsConstructor
public class StylistResponseAssembler {

    private final SearchProposalCodec searchProposalCodec;
    private final StylistProperties properties;

    public TurnResponse toTurnResponse(UUID conversationId, TurnRecord record, long userTurnsUsed) {
        return new TurnResponse(conversationId, ConversationStatus.OPEN, remainingTurns(userTurnsUsed),
                record.userMessage() == null ? null : toMessageResponse(record.userMessage()),
                toMessageResponse(record.assistantMessage()));
    }

    public ConversationResponse toConversationResponse(StylistConversation conversation, List<StylistMessage> messages) {
        long userTurnsUsed = StylistTurnCounter.countUserTurns(messages);
        return new ConversationResponse(conversation.getId(), conversation.getTitle(), conversation.getStatus(),
                remainingTurns(userTurnsUsed), messages.stream().map(this::toMessageResponse).toList(),
                conversation.getCreatedAt(), conversation.getUpdatedAt(), conversation.getCompletedAt());
    }

    public ConversationSummaryResponse toSummary(StylistConversation conversation) {
        return new ConversationSummaryResponse(conversation.getId(), conversation.getTitle(), conversation.getStatus(),
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private MessageResponse toMessageResponse(StylistMessage message) {
        return new MessageResponse(message.getId(), message.getSequence(), message.getRole(), message.getContent(),
                toProposalResponse(message.getProposal()), message.getCreatedAt());
    }

    private ProposalResponse toProposalResponse(String proposalJson) {
        if (proposalJson == null) {
            return null;
        }
        SearchProposal proposal = searchProposalCodec.decode(proposalJson);
        return new ProposalResponse(proposal.title(), proposal.summary());
    }

    private int remainingTurns(long userTurnsUsed) {
        return (int) Math.max(0, properties.maxUserTurns() - userTurnsUsed);
    }
}
