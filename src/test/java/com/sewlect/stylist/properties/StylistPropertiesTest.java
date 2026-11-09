package com.sewlect.stylist.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StylistPropertiesTest {

    @Test
    void staleThresholdAboveTheExecutionTimeout_isAccepted() {
        StylistProperties properties = withTimeouts(Duration.ofMinutes(10), Duration.ofMinutes(15));

        assertThat(properties.staleExecutionThreshold()).isGreaterThan(properties.executionTimeout());
    }

    @Test
    void staleThresholdEqualToTheExecutionTimeout_isRejected() {
        assertThatThrownBy(() -> withTimeouts(Duration.ofMinutes(10), Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stale-execution-threshold");
    }

    @Test
    void staleThresholdBelowTheExecutionTimeout_isRejected() {
        assertThatThrownBy(() -> withTimeouts(Duration.ofMinutes(10), Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingDurations_areLeftToBeanValidationRatherThanFailingTheComparison() {
        StylistProperties properties = withTimeouts(null, null);

        assertThat(properties.executionTimeout()).isNull();
    }

    private StylistProperties withTimeouts(Duration executionTimeout, Duration staleThreshold) {
        return new StylistProperties(15, 1000, 60, executionTimeout, staleThreshold, 300000L, 20, 100);
    }
}
