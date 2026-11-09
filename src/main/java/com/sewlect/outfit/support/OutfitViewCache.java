package com.sewlect.outfit.support;

import com.sewlect.common.cache.support.CacheSwitch;
import com.sewlect.outfit.domain.OutfitView;
import com.sewlect.outfit.properties.OutfitViewCacheProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@AllArgsConstructor
public class OutfitViewCache {

    private static final String KEY_PREFIX = "sewlect:outfit:view:v3:";

    private final RedisTemplate<String, OutfitView> outfitViewRedisTemplate;
    private final CacheSwitch cacheSwitch;
    private final OutfitViewCacheProperties properties;

    public Map<UUID, OutfitView> getAll(List<UUID> outfitIds) {
        if (!cacheSwitch.isActive() || outfitIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<String> keys = outfitIds.stream().map(this::keyFor).toList();
            List<OutfitView> values = outfitViewRedisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Map.of();
            }
            Map<UUID, OutfitView> hits = new HashMap<>();
            for (int i = 0; i < outfitIds.size(); i++) {
                OutfitView value = values.get(i);
                if (value != null) {
                    hits.put(outfitIds.get(i), value);
                }
            }
            return hits;
        } catch (RuntimeException e) {
            log.warn("Outfit view cache read failed open for {} outfit(s): {}", outfitIds.size(), e.getMessage());
            return Map.of();
        }
    }

    public void putAll(Map<UUID, OutfitView> responses) {
        if (!cacheSwitch.isActive() || responses.isEmpty()) {
            return;
        }
        try {
            outfitViewRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) {
                    responses.forEach((outfitId, response) -> outfitViewRedisTemplate.opsForValue()
                            .set(keyFor(outfitId), response, properties.ttl()));
                    return null;
                }
            });
        } catch (RuntimeException e) {
            log.warn("Outfit view cache write failed open for {} outfit(s): {}", responses.size(), e.getMessage());
        }
    }

    private String keyFor(UUID outfitId) {
        return KEY_PREFIX + outfitId;
    }
}