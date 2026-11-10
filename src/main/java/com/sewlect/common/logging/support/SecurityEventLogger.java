package com.sewlect.common.logging.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class SecurityEventLogger {

    public static final String AUDIT_LOGGER_NAME = "com.sewlect.security.audit";

    private static final Logger log = LoggerFactory.getLogger(AUDIT_LOGGER_NAME);

    public void registrationSucceeded(UUID userId) {
        log.info("event=registration outcome=success userId={}", userId);
    }

    public void loginSucceeded(UUID userId) {
        log.info("event=login outcome=success userId={}", userId);
    }

    public void loginFailed() {
        log.warn("event=login outcome=failure");
    }

    public void logoutSucceeded(UUID userId) {
        log.info("event=logout outcome=success userId={}", userId);
    }

    public void refreshTokenRotated(UUID userId) {
        log.info("event=token_rotation outcome=success userId={}", userId);
    }

    public void passwordChanged(UUID userId, int deletedTokenCount) {
        log.info("event=password_change outcome=success userId={} deletedTokens={}", userId, deletedTokenCount);
    }

    public void passwordChangeRejected(UUID userId) {
        log.warn("event=password_change outcome=failure userId={}", userId);
    }

    public void refreshTokenReuseDetected(UUID userId, int revokedTokenCount) {
        log.warn("event=token_reuse_detected outcome=all_tokens_revoked userId={} revokedTokens={}",
                userId, revokedTokenCount);
    }

    public void stylistMessageBlocked(UUID userId, List<String> categories) {
        log.warn("event=stylist_message_blocked userId={} categories={}", userId, String.join(",", categories));
    }

    public void stylistStrikeRecorded(UUID userId, String reason) {
        log.warn("event=stylist_strike userId={} reason={}", userId, reason);
    }

    public void stylistPaused(UUID userId, Duration duration) {
        log.warn("event=stylist_paused userId={} durationMinutes={}", userId, duration.toMinutes());
    }
}