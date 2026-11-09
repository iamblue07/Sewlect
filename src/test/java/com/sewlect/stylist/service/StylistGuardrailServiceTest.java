package com.sewlect.stylist.service;

import com.sewlect.common.ai.domain.SafetyVerdict;
import com.sewlect.common.ai.service.ContentSafetyService;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ContentPolicyViolationException;
import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.exception.ServiceUnavailableException;
import com.sewlect.common.logging.support.SecurityEventLogger;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.stylist.properties.StylistGuardrailProperties;
import com.sewlect.stylist.properties.StylistProperties;
import com.sewlect.stylist.support.StylistInputSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StylistGuardrailServiceTest {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private ContentSafetyService contentSafetyService;

    @Mock
    private SecurityEventLogger securityEventLogger;

    private StylistGuardrailService service;

    private final UUID userId = UUID.randomUUID();
    private final String subject = userId.toString();

    @BeforeEach
    void setUp() {
        StylistProperties properties = new StylistProperties(
                15, 1000, 60, Duration.ofMinutes(10), Duration.ofMinutes(15), 300000L, 20, 100);
        StylistGuardrailProperties guardrailProperties = new StylistGuardrailProperties(
                1500, 60, 800, 400, 3, Duration.ofHours(24), Duration.ofHours(24), 200, 10, "Outfits only.");
        service = new StylistGuardrailService(rateLimiter, contentSafetyService, new StylistInputSanitizer(),
                securityEventLogger, properties, guardrailProperties);
    }

    @Test
    void admitMessage_withinEveryLimit_consumesTheHourlyThenTheDailyBudget() {
        when(rateLimiter.tryConsume(eq(subject), anyString(), anyInt(), any())).thenReturn(true);

        service.admitMessage(userId);

        InOrder order = inOrder(rateLimiter);
        order.verify(rateLimiter).isExhausted(subject, "stylist-pause", 1);
        order.verify(rateLimiter).tryConsume(subject, "stylist-message", 60, HOUR);
        order.verify(rateLimiter).tryConsume(subject, "stylist-message-daily", 200, DAY);
    }

    @Test
    void admitMessage_pausedAccount_throwsRateLimitExceededWithoutConsumingAnyBudget() {
        when(rateLimiter.isExhausted(subject, "stylist-pause", 1)).thenReturn(true);

        assertThatThrownBy(() -> service.admitMessage(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("paused");
        verify(rateLimiter, never()).tryConsume(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void admitMessage_hourlyBudgetExhausted_throwsWithoutTouchingTheDailyBudget() {
        when(rateLimiter.tryConsume(subject, "stylist-message", 60, HOUR)).thenReturn(false);

        assertThatThrownBy(() -> service.admitMessage(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Stylist message limit exceeded");
        verify(rateLimiter, never()).tryConsume(subject, "stylist-message-daily", 200, DAY);
    }

    @Test
    void admitMessage_dailyBudgetExhausted_throwsTheDailyMessage() {
        when(rateLimiter.tryConsume(subject, "stylist-message", 60, HOUR)).thenReturn(true);
        when(rateLimiter.tryConsume(subject, "stylist-message-daily", 200, DAY)).thenReturn(false);

        assertThatThrownBy(() -> service.admitMessage(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Daily stylist message limit");
    }

    @Test
    void admitConversation_withinEveryLimit_alsoConsumesTheDailyChatBudget() {
        when(rateLimiter.tryConsume(eq(subject), anyString(), anyInt(), any())).thenReturn(true);

        service.admitConversation(userId);

        verify(rateLimiter).tryConsume(subject, "stylist-message", 60, HOUR);
        verify(rateLimiter).tryConsume(subject, "stylist-message-daily", 200, DAY);
        verify(rateLimiter).tryConsume(subject, "stylist-conversation-daily", 10, DAY);
    }

    @Test
    void admitConversation_dailyChatBudgetExhausted_throwsTheDailyChatMessage() {
        when(rateLimiter.tryConsume(subject, "stylist-message", 60, HOUR)).thenReturn(true);
        when(rateLimiter.tryConsume(subject, "stylist-message-daily", 200, DAY)).thenReturn(true);
        when(rateLimiter.tryConsume(subject, "stylist-conversation-daily", 10, DAY)).thenReturn(false);

        assertThatThrownBy(() -> service.admitConversation(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Daily limit of new stylist chats");
    }

    @Test
    void admitConversation_pausedAccount_neverConsumesTheChatBudget() {
        when(rateLimiter.isExhausted(subject, "stylist-pause", 1)).thenReturn(true);

        assertThatThrownBy(() -> service.admitConversation(userId)).isInstanceOf(RateLimitExceededException.class);
        verify(rateLimiter, never()).tryConsume(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void prepareUserMessage_returnsTheSanitizedText() {
        assertThat(service.prepareUserMessage("  wedding\u200B guest\u202E ")).isEqualTo("wedding guest");
    }

    @Test
    void prepareUserMessage_lengthIsMeasuredAfterInvisibleCharactersAreRemoved() {
        String padded = "a".repeat(1000) + "\u200B".repeat(50);

        assertThat(service.prepareUserMessage(padded)).hasSize(1000);
    }

    @Test
    void prepareUserMessage_overTheLimit_throwsBadRequest() {
        assertThatThrownBy(() -> service.prepareUserMessage("a".repeat(1001)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most 1000 characters");
    }

    @Test
    void prepareUserMessage_onlyInvisibleCharacters_throwsBadRequest() {
        assertThatThrownBy(() -> service.prepareUserMessage("\u200B\uFEFF"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(contentSafetyService);
    }

    @Test
    void requireSafeUserMessage_safe_recordsNothing() {
        when(contentSafetyService.checkUserMessage("a navy suit")).thenReturn(SafetyVerdict.SAFE);

        service.requireSafeUserMessage(userId, "a navy suit");

        verifyNoInteractions(rateLimiter, securityEventLogger);
    }

    @Test
    void requireSafeUserMessage_unsafe_logsTheCategoriesRecordsAStrikeAndThrows422() {
        when(contentSafetyService.checkUserMessage("harmful")).thenReturn(SafetyVerdict.unsafe(List.of("S1", "S10")));
        
        assertThatThrownBy(() -> service.requireSafeUserMessage(userId, "harmful"))
                .isInstanceOf(ContentPolicyViolationException.class)
                .hasMessageContaining("can't be sent to the stylist");
        verify(securityEventLogger).stylistMessageBlocked(userId, List.of("S1", "S10"));
        verify(securityEventLogger).stylistStrikeRecorded(userId, "BLOCKED_MESSAGE");
        verify(rateLimiter, never()).tryConsume(subject, "stylist-pause", 1, Duration.ofHours(24));
        verify(securityEventLogger, never()).stylistPaused(any(), any());
    }

    @Test
    void requireSafeUserMessage_strikeThatReachesTheLimit_pausesTheAccountForTheLockoutDuration() {
        when(contentSafetyService.checkUserMessage("harmful")).thenReturn(SafetyVerdict.unsafe(List.of("S10")));
        when(rateLimiter.isExhausted(subject, "stylist-strike", 3)).thenReturn(true);

        assertThatThrownBy(() -> service.requireSafeUserMessage(userId, "harmful"))
                .isInstanceOf(ContentPolicyViolationException.class);
        verify(rateLimiter).tryConsume(subject, "stylist-pause", 1, Duration.ofHours(24));
        verify(securityEventLogger).stylistPaused(userId, Duration.ofHours(24));
    }

    @Test
    void requireSafeUserMessage_safetyCheckUnavailable_propagatesWithoutAStrike() {
        when(contentSafetyService.checkUserMessage("text"))
                .thenThrow(new ServiceUnavailableException("The content safety check is unavailable - try again later"));

        assertThatThrownBy(() -> service.requireSafeUserMessage(userId, "text"))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(rateLimiter, securityEventLogger);
    }

    @Test
    void recordDeclinedTurn_recordsADeclinedStrike() {
        
        service.recordDeclinedTurn(userId);

        verify(securityEventLogger).stylistStrikeRecorded(userId, "DECLINED_TURN");
        verify(rateLimiter).tryConsume(subject, "stylist-strike", 3, Duration.ofHours(24));
        verify(securityEventLogger, never()).stylistPaused(any(), any());
    }

    @Test
    void recordDeclinedTurn_strikeThatReachesTheLimit_pausesTheAccount() {
        when(rateLimiter.isExhausted(subject, "stylist-strike", 3)).thenReturn(true);

        service.recordDeclinedTurn(userId);

        verify(rateLimiter).tryConsume(subject, "stylist-pause", 1, Duration.ofHours(24));
        verify(securityEventLogger).stylistPaused(userId, Duration.ofHours(24));
    }
}
