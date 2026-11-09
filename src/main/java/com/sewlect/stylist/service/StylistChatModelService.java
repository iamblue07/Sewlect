package com.sewlect.stylist.service;

import com.openai.errors.OpenAIException;
import com.sewlect.common.ai.config.DeepInfraConfig;
import com.sewlect.common.ai.service.ContentSafetyService;
import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.common.logging.enums.ExternalCallOutcome;
import com.sewlect.common.logging.support.ExternalCallLogger;
import com.sewlect.stylist.domain.AssistantTurn;
import com.sewlect.stylist.domain.StylistPromptContext;
import com.sewlect.stylist.entity.StylistMessage;
import com.sewlect.stylist.enums.AssistantTurnState;
import com.sewlect.stylist.enums.MessageRole;
import com.sewlect.stylist.properties.StylistGuardrailProperties;
import com.sewlect.stylist.support.StylistSystemPromptComposer;
import com.sewlect.stylist.support.StylistTurnRenderer;
import com.sewlect.stylist.support.StylistTurnValidator;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@AllArgsConstructor
public class StylistChatModelService {

    private static final String OPERATION_TURN = "stylist-turn";
    private static final String PROPOSAL_REQUEST_CONTEXT = "Please propose outfits for me.";
    private static final int MAX_ATTEMPTS = 2;

    private final OpenAiChatModel chatModel;
    private final StylistSystemPromptComposer systemPromptComposer;
    private final StylistTurnValidator turnValidator;
    private final StylistTurnRenderer turnRenderer;
    private final ContentSafetyService contentSafetyService;
    private final ExternalCallLogger externalCallLogger;
    private final StylistGuardrailProperties guardrailProperties;

    public AssistantTurn nextTurn(StylistPromptContext context, List<StylistMessage> history,
                                  String newUserContentOrNull, boolean proposalRequested) {
        String canary = newCanary();
        List<Message> messages = buildMessages(context, history, newUserContentOrNull, proposalRequested, canary);
        String userContext = newUserContentOrNull != null ? newUserContentOrNull : lastUserContent(history);

        List<String> fallbackReplies = new ArrayList<>();
        boolean blocked = false;
        for (int attemptNumber = 0; attemptNumber < MAX_ATTEMPTS; attemptNumber++) {
            switch (attempt(messages, canary, userContext)) {
                case Attempt.Accepted accepted -> {
                    return accepted.turn();
                }
                case Attempt.Fallback fallback -> fallbackReplies.addFirst(fallback.reply());
                case Attempt.Blocked _ -> blocked = true;
                case Attempt.Rejected _ -> {
                }
            }
        }
        return fallbackTurn(userContext, fallbackReplies, blocked);
    }

    private AssistantTurn fallbackTurn(String userContext, List<String> fallbackReplies, boolean blocked) {
        for (String reply : fallbackReplies) {
            if (contentSafetyService.checkAssistantReply(userContext, reply).safe()) {
                return AssistantTurn.withoutProposal(AssistantTurnState.CLARIFYING, reply);
            }
        }
        if (blocked || !fallbackReplies.isEmpty()) {
            return AssistantTurn.withoutProposal(AssistantTurnState.DECLINED, guardrailProperties.refusalMessage());
        }
        throw new ExternalServiceException("DeepInfra stylist turn returned no usable reply");
    }

    private List<Message> buildMessages(StylistPromptContext context, List<StylistMessage> history,
                                        String newUserContentOrNull, boolean proposalRequested, String canary) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPromptComposer.compose(context, proposalRequested, canary)));
        for (StylistMessage message : history) {
            messages.add(message.getRole() == MessageRole.USER
                    ? new UserMessage(message.getContent())
                    : new AssistantMessage(turnRenderer.renderHistoryMessage(message)));
        }
        if (newUserContentOrNull != null) {
            messages.add(new UserMessage(newUserContentOrNull));
        }
        return messages;
    }

    private Attempt attempt(List<Message> messages, String canary, String userContext) {
        long startedAt = System.nanoTime();
        AssistantTurn turn;
        try {
            turn = ChatClient.create(chatModel).prompt()
                    .messages(messages)
                    .call()
                    .entity(AssistantTurn.class);
        } catch (RuntimeException e) {
            if (isTransportFailure(e)) {
                logTurn(startedAt, ExternalCallOutcome.PERMANENT_FAILURE);
                throw new ExternalServiceException("DeepInfra stylist turn failed: " + e.getMessage());
            }
            logTurn(startedAt, ExternalCallOutcome.RETRYABLE_FAILURE);
            return new Attempt.Rejected();
        }

        if (turnValidator.leaksCanary(turn, canary)) {
            logTurn(startedAt, ExternalCallOutcome.RETRYABLE_FAILURE);
            return new Attempt.Blocked();
        }

        if (!turnValidator.isValid(turn)) {
            logTurn(startedAt, ExternalCallOutcome.RETRYABLE_FAILURE);
            String reply = turnValidator.usableReply(turn);
            return reply == null ? new Attempt.Rejected() : new Attempt.Fallback(reply);
        }

        logTurn(startedAt, ExternalCallOutcome.SUCCESS);
        AssistantTurn normalized = turnValidator.normalize(turn);
        if (!contentSafetyService.checkAssistantReply(userContext, turnRenderer.renderForSafetyCheck(normalized)).safe()) {
            return new Attempt.Blocked();
        }
        return new Attempt.Accepted(normalized);
    }

    private String lastUserContent(List<StylistMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getRole() == MessageRole.USER) {
                return history.get(i).getContent();
            }
        }
        return PROPOSAL_REQUEST_CONTEXT;
    }

    private String newCanary() {
        return "SWL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private void logTurn(long startedAt, ExternalCallOutcome outcome) {
        externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION_TURN,
                ExternalCallLogger.elapsedMillisSince(startedAt), outcome);
    }

    private boolean isTransportFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenAIException) {
                return true;
            }
        }
        return false;
    }

    private sealed interface Attempt {

        record Accepted(AssistantTurn turn) implements Attempt {
        }

        record Fallback(String reply) implements Attempt {
        }

        record Blocked() implements Attempt {
        }

        record Rejected() implements Attempt {
        }
    }
}
