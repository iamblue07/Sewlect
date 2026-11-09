package com.sewlect.common.ai.service;

import com.sewlect.common.ai.config.DeepInfraConfig;
import com.sewlect.common.ai.domain.SafetyVerdict;
import com.sewlect.common.exception.ServiceUnavailableException;
import com.sewlect.common.logging.enums.ExternalCallOutcome;
import com.sewlect.common.logging.support.ExternalCallLogger;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class ContentSafetyService {

    private static final String OPERATION = "content-safety";
    private static final String UNAVAILABLE_MESSAGE = "The content safety check is unavailable - try again later";
    private static final Pattern HAZARD_CATEGORY = Pattern.compile("S\\d{1,2}");
    private static final int MAX_LOGGED_OUTPUT_LENGTH = 100;

    @Qualifier("deepInfraGuardModel")
    private final OpenAiChatModel guardModel;
    private final ExternalCallLogger externalCallLogger;

    public SafetyVerdict checkUserMessage(String content) {
        return classify(List.of(new UserMessage(content)));
    }

    public SafetyVerdict checkAssistantReply(String userMessage, String assistantReply) {
        return classify(List.of(new UserMessage(userMessage), new AssistantMessage(assistantReply)));
    }

    private SafetyVerdict classify(List<Message> conversation) {
        long startedAt = System.nanoTime();
        String output;
        try {
            output = guardModel.call(new Prompt(conversation)).getResult().getOutput().getText();
        } catch (RuntimeException e) {
            log.warn("Content safety check failed: {}", e.getMessage());
            logCall(startedAt, ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }

        SafetyVerdict verdict = parse(output);
        if (verdict == null) {
            log.warn("Content safety check returned an unrecognised verdict: {}",
                    output == null ? null : StringUtils.truncate(output.strip(), MAX_LOGGED_OUTPUT_LENGTH));
            logCall(startedAt, ExternalCallOutcome.PERMANENT_FAILURE);
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
        logCall(startedAt, ExternalCallOutcome.SUCCESS);
        return verdict;
    }

    private SafetyVerdict parse(String output) {
        if (output == null) {
            return null;
        }
        String normalized = output.strip().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("UNSAFE")) {
            return SafetyVerdict.unsafe(HAZARD_CATEGORY.matcher(normalized).results()
                    .map(MatchResult::group)
                    .distinct()
                    .toList());
        }
        if (normalized.startsWith("SAFE")) {
            return SafetyVerdict.SAFE;
        }
        return null;
    }

    private void logCall(long startedAt, ExternalCallOutcome outcome) {
        externalCallLogger.logCall(DeepInfraConfig.PROVIDER, OPERATION,
                ExternalCallLogger.elapsedMillisSince(startedAt), outcome);
    }
}
