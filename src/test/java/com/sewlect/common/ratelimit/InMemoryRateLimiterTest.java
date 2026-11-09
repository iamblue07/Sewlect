package com.sewlect.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(clock);

    @Test
    void tryConsume_upToLimitRequestsSucceed_thenTheNextOneFails() {
        String subject = UUID.randomUUID().toString();
        String operationKey = "outfit-prompt-generation";

        for (int i = 0; i < 200; i++) {
            assertThat(rateLimiter.tryConsume(subject, operationKey, 200, Duration.ofHours(1))).isTrue();
        }

        assertThat(rateLimiter.tryConsume(subject, operationKey, 200, Duration.ofHours(1))).isFalse();
    }

    @Test
    void tryConsume_differentOperationKeys_trackedIndependently() {
        String subject = UUID.randomUUID().toString();

        for (int i = 0; i < 200; i++) {
            assertThat(rateLimiter.tryConsume(subject, "generation", 200, Duration.ofHours(1))).isTrue();
        }
        assertThat(rateLimiter.tryConsume(subject, "generation", 200, Duration.ofHours(1))).isFalse();

        assertThat(rateLimiter.tryConsume(subject, "refinement", 200, Duration.ofHours(1))).isTrue();
    }

    @Test
    void tryConsume_differentSubjects_trackedIndependently() {
        String subjectA = UUID.randomUUID().toString();
        String subjectB = UUID.randomUUID().toString();
        String operationKey = "outfit-prompt-generation";

        for (int i = 0; i < 200; i++) {
            assertThat(rateLimiter.tryConsume(subjectA, operationKey, 200, Duration.ofHours(1))).isTrue();
        }
        assertThat(rateLimiter.tryConsume(subjectA, operationKey, 200, Duration.ofHours(1))).isFalse();

        assertThat(rateLimiter.tryConsume(subjectB, operationKey, 200, Duration.ofHours(1))).isTrue();
    }

    @Test
    void tryConsume_nonUuidSubjects_areValidKeysTrackedIndependently() {
        assertThat(rateLimiter.tryConsume("203.0.113.7", "auth-ip", 1, Duration.ofMinutes(15))).isTrue();
        assertThat(rateLimiter.tryConsume("203.0.113.7", "auth-ip", 1, Duration.ofMinutes(15))).isFalse();

        assertThat(rateLimiter.tryConsume("jane@example.com", "auth-email", 1, Duration.ofMinutes(15))).isTrue();
        assertThat(rateLimiter.tryConsume("203.0.113.8", "auth-ip", 1, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    void tryConsume_afterWindowExpires_resetsTheCount() {
        String subject = UUID.randomUUID().toString();
        String operationKey = "outfit-prompt-generation";

        assertThat(rateLimiter.tryConsume(subject, operationKey, 1, Duration.ofHours(1))).isTrue();
        assertThat(rateLimiter.tryConsume(subject, operationKey, 1, Duration.ofHours(1))).isFalse();

        clock.advance(Duration.ofHours(1).plusSeconds(1));

        assertThat(rateLimiter.tryConsume(subject, operationKey, 1, Duration.ofHours(1))).isTrue();
    }

    @Test
    void evictExpiredWindows_doesNotDisruptAnActiveSubjectsWindow() {
        String staleSubject = UUID.randomUUID().toString();
        String activeSubject = UUID.randomUUID().toString();
        String operationKey = "outfit-prompt-generation";

        rateLimiter.tryConsume(staleSubject, operationKey, 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(2));
        rateLimiter.tryConsume(activeSubject, operationKey, 1, Duration.ofHours(1));

        rateLimiter.evictExpiredWindows();

        assertThat(rateLimiter.tryConsume(activeSubject, operationKey, 1, Duration.ofHours(1))).isFalse();
        assertThat(rateLimiter.tryConsume(staleSubject, operationKey, 1, Duration.ofMinutes(1))).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Test
    void tryConsume_permits_aRequestLargerThanTheRemainingBudgetConsumesNothing() {
        String subject = UUID.randomUUID().toString();
        Duration hour = Duration.ofHours(1);

        assertThat(rateLimiter.tryConsume(subject, "generation", 2, hour, 3)).isFalse();
        assertThat(rateLimiter.tryConsume(subject, "generation", 2, hour, 2)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, "generation", 2, hour, 1)).isFalse();
    }

    @Test
    void tryConsume_permits_partialBudgetAdmitsOnlyRequestsThatFit() {
        String subject = UUID.randomUUID().toString();
        Duration hour = Duration.ofHours(1);

        assertThat(rateLimiter.tryConsume(subject, "generation", 5, hour, 4)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, "generation", 5, hour, 2)).isFalse();
        assertThat(rateLimiter.tryConsume(subject, "generation", 5, hour, 1)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, "generation", 5, hour)).isFalse();
    }

    @Test
    void isExhausted_noWindowYet_isFalse() {
        assertThat(rateLimiter.isExhausted(UUID.randomUUID().toString(), "stylist-pause", 1)).isFalse();
    }

    @Test
    void isExhausted_belowTheLimit_isFalseAndAtTheLimit_isTrue() {
        String subject = UUID.randomUUID().toString();

        rateLimiter.tryConsume(subject, "stylist-strike", 3, Duration.ofHours(24));
        assertThat(rateLimiter.isExhausted(subject, "stylist-strike", 2)).isFalse();

        rateLimiter.tryConsume(subject, "stylist-strike", 3, Duration.ofHours(24));
        assertThat(rateLimiter.isExhausted(subject, "stylist-strike", 2)).isTrue();
    }

    @Test
    void isExhausted_afterTheWindowExpires_isFalseAgain() {
        String subject = UUID.randomUUID().toString();
        rateLimiter.tryConsume(subject, "stylist-pause", 1, Duration.ofHours(24));
        assertThat(rateLimiter.isExhausted(subject, "stylist-pause", 1)).isTrue();

        clock.advance(Duration.ofHours(24).plusSeconds(1));

        assertThat(rateLimiter.isExhausted(subject, "stylist-pause", 1)).isFalse();
    }

    @Test
    void isExhausted_neverStartsAWindowSoALaterConsumeGetsTheFullWindow() {
        String subject = UUID.randomUUID().toString();
        assertThat(rateLimiter.isExhausted(subject, "stylist-pause", 1)).isFalse();

        clock.advance(Duration.ofHours(20));
        rateLimiter.tryConsume(subject, "stylist-pause", 1, Duration.ofHours(24));
        clock.advance(Duration.ofHours(23));

        assertThat(rateLimiter.isExhausted(subject, "stylist-pause", 1)).isTrue();
    }

    @Test
    void isExhausted_otherOperationKeysAndSubjects_areIndependent() {
        String subject = UUID.randomUUID().toString();
        rateLimiter.tryConsume(subject, "stylist-pause", 1, Duration.ofHours(24));

        assertThat(rateLimiter.isExhausted(subject, "stylist-strike", 1)).isFalse();
        assertThat(rateLimiter.isExhausted(UUID.randomUUID().toString(), "stylist-pause", 1)).isFalse();
    }
}
