package com.sewlect.stylist.service;

import com.sewlect.common.ai.domain.SafetyVerdict;
import com.sewlect.common.ai.service.ContentSafetyService;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ContentPolicyViolationException;
import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.logging.support.SecurityEventLogger;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.stylist.enums.StylistStrikeReason;
import com.sewlect.stylist.properties.StylistGuardrailProperties;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.support.StylistInputSanitizer;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@AllArgsConstructor
@EnableConfigurationProperties(StylistGuardrailProperties.class)
public class StylistGuardrailService {

    private static final String MESSAGE_HOURLY_KEY = "stylist-message";
    private static final String MESSAGE_DAILY_KEY = "stylist-message-daily";
    private static final String CONVERSATION_DAILY_KEY = "stylist-conversation-daily";
    private static final String STRIKE_KEY = "stylist-strike";
    private static final String PAUSE_KEY = "stylist-pause";
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final RateLimiter rateLimiter;
    private final ContentSafetyService contentSafetyService;
    private final StylistInputSanitizer inputSanitizer;
    private final SecurityEventLogger securityEventLogger;
    private final StylistProperties properties;
    private final StylistGuardrailProperties guardrailProperties;

    public void admitConversation(UUID userId) {
        admitMessage(userId);
        consume(userId, CONVERSATION_DAILY_KEY, guardrailProperties.dailyConversationLimit(), DAY,
                "Daily limit of new stylist chats reached - try again tomorrow");
    }

    public void admitMessage(UUID userId) {
        if (rateLimiter.isExhausted(userId.toString(), PAUSE_KEY, 1)) {
            throw new RateLimitExceededException(
                    "The stylist is paused for this account after repeated blocked messages - try again later");
        }
        consume(userId, MESSAGE_HOURLY_KEY, properties.messageRateLimitPerHour(), HOUR,
                "Stylist message limit exceeded - try again later");
        consume(userId, MESSAGE_DAILY_KEY, guardrailProperties.dailyMessageLimit(), DAY,
                "Daily stylist message limit reached - try again tomorrow");
    }

    public String prepareUserMessage(String rawContent) {
        String content = inputSanitizer.sanitize(rawContent);
        if (content.length() > properties.maxMessageLength()) {
            throw new BadRequestException(
                    "A message may be at most " + properties.maxMessageLength() + " characters long");
        }
        return content;
    }

    public void requireSafeUserMessage(UUID userId, String content) {
        SafetyVerdict verdict = contentSafetyService.checkUserMessage(content);
        if (verdict.safe()) {
            return;
        }
        securityEventLogger.stylistMessageBlocked(userId, verdict.categories());
        recordStrike(userId, StylistStrikeReason.BLOCKED_MESSAGE);
        throw new ContentPolicyViolationException(
                "This message can't be sent to the stylist - please keep the chat about outfits and styling");
    }

    public void recordDeclinedTurn(UUID userId) {
        recordStrike(userId, StylistStrikeReason.DECLINED_TURN);
    }

    private void recordStrike(UUID userId, StylistStrikeReason reason) {
        String subject = userId.toString();
        int strikeLimit = guardrailProperties.strikeLimit();
        securityEventLogger.stylistStrikeRecorded(userId, reason.name());
        rateLimiter.tryConsume(subject, STRIKE_KEY, strikeLimit, guardrailProperties.strikeWindow());
        if (rateLimiter.isExhausted(subject, STRIKE_KEY, strikeLimit)) {
            rateLimiter.tryConsume(subject, PAUSE_KEY, 1, guardrailProperties.lockoutDuration());
            securityEventLogger.stylistPaused(userId, guardrailProperties.lockoutDuration());
        }
    }

    private void consume(UUID userId, String operationKey, int limit, Duration window, String rejectionMessage) {
        if (!rateLimiter.tryConsume(userId.toString(), operationKey, limit, window)) {
            throw new RateLimitExceededException(rejectionMessage);
        }
    }
}
