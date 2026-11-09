package com.sewlect.common.logging.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalCallLoggerTest {

    @Test
    void elapsedMillisSince_aStartTwoSecondsAgo_reportsAtLeastTwoThousandMillis() {
        long startedAt = System.nanoTime() - Duration.ofSeconds(2).toNanos();

        assertThat(ExternalCallLogger.elapsedMillisSince(startedAt)).isBetween(2_000L, 60_000L);
    }

    @Test
    void elapsedMillisSince_justNow_isNeverNegative() {
        assertThat(ExternalCallLogger.elapsedMillisSince(System.nanoTime())).isBetween(0L, 60_000L);
    }
}
