package com.sewlect.common.logging.support;

import com.sewlect.common.logging.enums.ExternalCallOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExternalCallLogger {

    public static final String EXTERNAL_LOGGER_NAME = "com.sewlect.external.calls";

    private static final Logger log = LoggerFactory.getLogger(EXTERNAL_LOGGER_NAME);

    public static long elapsedMillisSince(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    public void logCall(String provider, String operation, long durationMs, ExternalCallOutcome outcome) {
        if (outcome == ExternalCallOutcome.SUCCESS) {
            log.info("provider={} operation={} durationMs={} outcome={}",
                    provider, operation, durationMs, outcome.logValue());
            return;
        }
        log.warn("provider={} operation={} durationMs={} outcome={}",
                provider, operation, durationMs, outcome.logValue());
    }
}