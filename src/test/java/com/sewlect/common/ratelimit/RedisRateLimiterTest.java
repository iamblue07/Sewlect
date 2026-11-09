package com.sewlect.common.ratelimit;

import com.sewlect.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RedisRateLimiterTest extends AbstractRedisIntegrationTest {

    private static final String OPERATION_KEY = "outfit-prompt-generation";
    private static final Duration HOUR = Duration.ofHours(1);

    private RedisRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisRateLimiter(REDIS_TEMPLATE);
    }

    @Test
    void tryConsume_requestsUpToTheLimit_succeedAndTheNextIsRejected() {
        String subject = UUID.randomUUID().toString();

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 5, HOUR)).isTrue();
        }
        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 5, HOUR)).isFalse();
    }

    @Test
    void tryConsume_differentOperationKeys_trackedIndependently() {
        String subject = UUID.randomUUID().toString();

        assertThat(rateLimiter.tryConsume(subject, "generation", 1, HOUR)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, "generation", 1, HOUR)).isFalse();

        assertThat(rateLimiter.tryConsume(subject, "refinement", 1, HOUR)).isTrue();
    }

    @Test
    void tryConsume_differentSubjects_trackedIndependently() {
        String subjectA = UUID.randomUUID().toString();
        String subjectB = UUID.randomUUID().toString();

        assertThat(rateLimiter.tryConsume(subjectA, OPERATION_KEY, 1, HOUR)).isTrue();
        assertThat(rateLimiter.tryConsume(subjectA, OPERATION_KEY, 1, HOUR)).isFalse();

        assertThat(rateLimiter.tryConsume(subjectB, OPERATION_KEY, 1, HOUR)).isTrue();
    }

    @Test
    void tryConsume_afterWindowExpires_admitsAgain() {
        String subject = UUID.randomUUID().toString();
        Duration window = Duration.ofMillis(500);

        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 1, window)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 1, window)).isFalse();

        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> rateLimiter.tryConsume(subject, OPERATION_KEY, 1, window));
    }

    @Test
    void tryConsume_redisUnavailable_rejectsRatherThanAdmits() throws IOException {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", unusedPort()));
        unreachable.afterPropertiesSet();
        try {
            RedisRateLimiter limiter = new RedisRateLimiter(new StringRedisTemplate(unreachable));

            assertThat(limiter.tryConsume(UUID.randomUUID().toString(), OPERATION_KEY, 1000, HOUR)).isFalse();
        } finally {
            unreachable.destroy();
        }
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void tryConsume_permits_aRequestLargerThanTheRemainingBudgetConsumesNothing() {
        String subject = UUID.randomUUID().toString();

        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 2, HOUR, 3)).isFalse();
        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 2, HOUR, 2)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 2, HOUR, 1)).isFalse();
    }

    @Test
    void tryConsume_permits_partialBudgetAdmitsOnlyRequestsThatFit() {
        String subject = UUID.randomUUID().toString();

        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 5, HOUR, 4)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 5, HOUR, 2)).isFalse();
        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 5, HOUR, 1)).isTrue();
        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 5, HOUR)).isFalse();
    }

    @Test
    void tryConsume_refusedFirstRequest_stillLeavesAnExpiringKeyRatherThanAnImmortalOne() {
        String subject = UUID.randomUUID().toString();

        assertThat(rateLimiter.tryConsume(subject, OPERATION_KEY, 2, HOUR, 3)).isFalse();

        Long ttlMillis = REDIS_TEMPLATE.getExpire("sewlect:rl:v1:" + OPERATION_KEY + ":" + subject,
                TimeUnit.MILLISECONDS);
        assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(HOUR.toMillis());
    }

    @Test
    void isExhausted_noKeyYet_isFalseAndCreatesNoKey() {
        String subject = UUID.randomUUID().toString();

        assertThat(rateLimiter.isExhausted(subject, "stylist-pause", 1)).isFalse();
        assertThat(REDIS_TEMPLATE.hasKey("sewlect:rl:v1:stylist-pause:" + subject)).isFalse();
    }

    @Test
    void isExhausted_belowTheLimit_isFalseAndAtTheLimit_isTrue() {
        String subject = UUID.randomUUID().toString();

        rateLimiter.tryConsume(subject, "stylist-strike", 3, HOUR);
        assertThat(rateLimiter.isExhausted(subject, "stylist-strike", 2)).isFalse();

        rateLimiter.tryConsume(subject, "stylist-strike", 3, HOUR);
        assertThat(rateLimiter.isExhausted(subject, "stylist-strike", 2)).isTrue();
    }

    @Test
    void isExhausted_afterTheWindowExpires_isFalseAgain() {
        String subject = UUID.randomUUID().toString();
        rateLimiter.tryConsume(subject, "stylist-pause", 1, Duration.ofMillis(500));
        assertThat(rateLimiter.isExhausted(subject, "stylist-pause", 1)).isTrue();

        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> !rateLimiter.isExhausted(subject, "stylist-pause", 1));
    }

    @Test
    void isExhausted_redisUnavailable_failsClosedAsExhausted() throws IOException {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", unusedPort()));
        unreachable.afterPropertiesSet();
        try {
            RedisRateLimiter limiter = new RedisRateLimiter(new StringRedisTemplate(unreachable));

            assertThat(limiter.isExhausted(UUID.randomUUID().toString(), "stylist-pause", 1)).isTrue();
        } finally {
            unreachable.destroy();
        }
    }
}
