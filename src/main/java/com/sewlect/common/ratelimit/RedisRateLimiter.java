package com.sewlect.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "common.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "sewlect:rl:v1:";
    private static final String SCRIPT_LOCATION = "redis/rate-limit-fixed-window.lua";
    private static final long WITHIN_LIMIT = 1L;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource(SCRIPT_LOCATION));
        this.script.setResultType(Long.class);
    }

    @Override
    public boolean tryConsume(String subject, String operationKey, int limit, Duration window, int permits) {
        String key = keyFor(subject, operationKey);
        try {
            Long result = redisTemplate.execute(script, List.of(key),
                    String.valueOf(limit), String.valueOf(window.toMillis()), String.valueOf(permits));
            return result != null && result == WITHIN_LIMIT;
        } catch (RuntimeException e) {
            log.warn("Rate limit check for operation {} failed closed because Redis is unavailable: {}",
                    operationKey, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isExhausted(String subject, String operationKey, int limit) {
        try {
            String count = redisTemplate.opsForValue().get(keyFor(subject, operationKey));
            return count != null && Long.parseLong(count) >= limit;
        } catch (RuntimeException e) {
            log.warn("Rate limit exhaustion check for operation {} failed closed because Redis is unavailable: {}",
                    operationKey, e.getMessage());
            return true;
        }
    }

    private String keyFor(String subject, String operationKey) {
        return KEY_PREFIX + operationKey + ":" + subject;
    }
}