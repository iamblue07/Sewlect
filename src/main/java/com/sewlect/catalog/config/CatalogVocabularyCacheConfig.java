package com.sewlect.catalog.config;

import com.sewlect.catalog.properties.CatalogVocabularyCacheProperties;
import com.sewlect.common.cache.support.RedisCacheDefinition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
@EnableConfigurationProperties(CatalogVocabularyCacheProperties.class)
public class CatalogVocabularyCacheConfig {

    public static final String CATALOG_OCCASIONS_CACHE = "catalogOccasions";

    @Bean
    public RedisCacheDefinition catalogOccasionsCacheDefinition(RedisCacheConfiguration defaultRedisCacheConfiguration,
                                                                JsonMapper jsonMapper,
                                                                CatalogVocabularyCacheProperties properties) {
        JavaType occasionsType = jsonMapper.getTypeFactory().constructCollectionType(List.class, String.class);
        return new RedisCacheDefinition(CATALOG_OCCASIONS_CACHE, defaultRedisCacheConfiguration
                .entryTtl(properties.ttl())
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper, occasionsType))));
    }
}
