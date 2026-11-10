package com.sewlect.stylist.service;

import com.sewlect.common.exception.ConflictException;
import com.sewlect.stylist.domain.AssistantTurn;
import com.sewlect.stylist.domain.StylistPromptContext;
import com.sewlect.stylist.domain.TurnRecord;
import com.sewlect.stylist.dto.TurnResponse;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.AssistantTurnState;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.repository.StylistMessageRepository;
import com.sewlect.stylist.support.StylistPromptContextFactory;
import com.sewlect.stylist.support.StylistResponseAssembler;
import com.sewlect.stylist.util.StylistTurnCounter;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(StylistProperties.class)
public class StylistConversationService {

    private final StylistConversationQueryService conversationQueryService;
    private final StylistMessageRepository messageRepository;
    private final StylistPersistenceService stylistPersistenceService;
    private final StylistChatModelService stylistChatModelService;
    private final StylistPromptContextFactory promptContextFactory;
    private final StylistGuardrailService guardrailService;
    private final StylistResponseAssembler responseAssembler;
    private final StylistProperties properties;

    public TurnResponse start(UUID userId, String rawMessage) {
        guardrailService.admitConversation(userId);
        String message = guardrailService.prepareUserMessage(rawMessage);
        guardrailService.requireSafeUserMessage(userId, message);

        StylistPromptContext context = promptContextFactory.build(userId);
        AssistantTurn turn = stylistChatModelService.nextTurn(context, List.of(), message, false);
        TurnRecord record = stylistPersistenceService.createWithTurn(userId, message, turn);
        recordIfDeclined(userId, turn);

        return responseAssembler.toTurnResponse(record.assistantMessage().getConversation().getId(), record, 1);
    }

    public TurnResponse sendMessage(UUID userId, UUID conversationId, String rawContent) {
        guardrailService.admitMessage(userId);
        String content = guardrailService.prepareUserMessage(rawContent);

        conversationQueryService.getOwnedOpen(userId, conversationId);
        List<StylistMessage> history = messageRepository.findByConversationIdOrderBySequenceAsc(conversationId);
        long userTurns = StylistTurnCounter.countUserTurns(history);
        if (userTurns >= properties.maxUserTurns()) {
            throw new ConflictException("Turn limit reached - ask for outfits or start a new chat");
        }
        guardrailService.requireSafeUserMessage(userId, content);

        AssistantTurn turn = stylistChatModelService.nextTurn(
                promptContextFactory.build(userId), history, content, false);
        TurnRecord record = stylistPersistenceService.appendTurn(conversationId, nextSequence(history), content, turn);
        recordIfDeclined(userId, turn);
        return responseAssembler.toTurnResponse(conversationId, record, userTurns + 1);
    }

    public TurnResponse requestProposal(UUID userId, UUID conversationId) {
        guardrailService.admitMessage(userId);

        conversationQueryService.getOwnedOpen(userId, conversationId);
        List<StylistMessage> history = messageRepository.findByConversationIdOrderBySequenceAsc(conversationId);
        long userTurns = StylistTurnCounter.countUserTurns(history);

        AssistantTurn turn = stylistChatModelService.nextTurn(
                promptContextFactory.build(userId), history, null, true);
        TurnRecord record = stylistPersistenceService.appendTurn(conversationId, nextSequence(history), null, turn);
        return responseAssembler.toTurnResponse(conversationId, record, userTurns);
    }

    public void delete(UUID userId, UUID conversationId) {
        conversationQueryService.getOwned(userId, conversationId);
        if (!stylistPersistenceService.deleteUnlessExecuting(conversationId)) {
            throw new ConflictException("The conversation is running its searches and cannot be deleted yet");
        }
    }

    private void recordIfDeclined(UUID userId, AssistantTurn turn) {
        if (turn.state() == AssistantTurnState.DECLINED) {
            guardrailService.recordDeclinedTurn(userId);
        }
    }

    private int nextSequence(List<StylistMessage> history) {
        return history.isEmpty() ? 0 : history.get(history.size() - 1).getSequence() + 1;
    }
}
