package com.sewlect.outfit.config;

import com.sewlect.outfit.domain.OutfitView;
import com.sewlect.outfit.properties.OutfitViewCacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(OutfitViewCacheProperties.class)
public class OutfitViewCacheConfig {

    @Bean
    public RedisTemplate<String, OutfitView> outfitViewRedisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                                         JsonMapper jsonMapper) {
        RedisTemplate<String, OutfitView> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, OutfitView.class));
        return template;
    }
}