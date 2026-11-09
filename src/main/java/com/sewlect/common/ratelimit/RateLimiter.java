package com.sewlect.common.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    default boolean tryConsume(String subject, String operationKey, int limit, Duration window) {
        return tryConsume(subject, operationKey, limit, window, 1);
    }

    boolean tryConsume(String subject, String operationKey, int limit, Duration window, int permits);

    boolean isExhausted(String subject, String operationKey, int limit);
}
