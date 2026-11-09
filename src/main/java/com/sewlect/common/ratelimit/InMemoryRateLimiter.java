package com.sewlect.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@ConditionalOnProperty(name = "common.redis.enabled", havingValue = "false")
public class InMemoryRateLimiter implements RateLimiter {

    private final Map<RateLimitKey, WindowState> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryConsume(String subject, String operationKey, int limit, Duration window, int permits) {
        RateLimitKey key = new RateLimitKey(subject, operationKey);
        Instant now = clock.instant();
        AtomicBoolean consumed = new AtomicBoolean(false);

        windows.compute(key, (k, existing) -> {
            WindowState current = (existing == null || existing.isExpired(now))
                    ? new WindowState(now, window, 0)
                    : existing;

            if (current.count() + permits > limit) {
                return current;
            }

            consumed.set(true);
            return current.increment(permits);
        });

        return consumed.get();
    }

    @Override
    public boolean isExhausted(String subject, String operationKey, int limit) {
        WindowState current = windows.get(new RateLimitKey(subject, operationKey));
        return current != null && !current.isExpired(clock.instant()) && current.count() >= limit;
    }

    @Scheduled(fixedRateString = "${common.ratelimit.sweep-interval-ms}")
    public void evictExpiredWindows() {
        Instant now = clock.instant();
        int sizeBefore = windows.size();
        windows.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        int evicted = sizeBefore - windows.size();
        if (evicted > 0) {
            log.debug("Rate limiter sweep evicted {} expired window(s), {} remaining", evicted, windows.size());
        }
    }

    private record RateLimitKey(String subject, String operationKey) {
    }

    private record WindowState(Instant windowStart, Duration window, int count) {

        boolean isExpired(Instant now) {
            return now.isAfter(windowStart.plus(window));
        }

        WindowState increment(int permits) {
            return new WindowState(windowStart, window, count + permits);
        }
    }
}