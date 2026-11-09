package com.sewlect.stylist.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stylist.executor")
public record StylistExecutorProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity
) {
}
