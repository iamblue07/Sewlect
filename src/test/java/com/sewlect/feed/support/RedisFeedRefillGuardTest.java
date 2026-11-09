package com.sewlect.feed.support;

import com.sewlect.feed.properties.FeedProperties;
import com.sewlect.support.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RedisFeedRefillGuardTest extends AbstractRedisIntegrationTest {

    private static final long LONG_TTL_MS = 300000;
    private static final long SHORT_TTL_MS = 500;

    private RedisFeedRefillGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RedisFeedRefillGuard(REDIS_TEMPLATE, new FeedProperties(20, 30, LONG_TTL_MS, 20));
    }

    @Test
    void tryClaim_firstClaimForUser_succeeds() {
        assertThat(guard.tryClaim(UUID.randomUUID())).isTrue();
    }

    @Test
    void tryClaim_whileClaimIsHeld_isRejected() {
        UUID userId = UUID.randomUUID();

        assertThat(guard.tryClaim(userId)).isTrue();
        assertThat(guard.tryClaim(userId)).isFalse();

        assertThat(guard.tryClaim(UUID.randomUUID())).isTrue();
    }

    @Test
    void release_allowsTheUserToClaimAgain() {
        UUID userId = UUID.randomUUID();
        guard.tryClaim(userId);

        guard.release(userId);

        assertThat(guard.tryClaim(userId)).isTrue();
    }

    @Test
    void tryClaim_strandedClaimIsFreedOnceTheTtlExpires() {
        RedisFeedRefillGuard shortLivedGuard =
                new RedisFeedRefillGuard(REDIS_TEMPLATE, new FeedProperties(20, 30, SHORT_TTL_MS, 20));
        UUID userId = UUID.randomUUID();

        assertThat(shortLivedGuard.tryClaim(userId)).isTrue();
        assertThat(shortLivedGuard.tryClaim(userId)).isFalse();

        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> shortLivedGuard.tryClaim(userId));
    }
}