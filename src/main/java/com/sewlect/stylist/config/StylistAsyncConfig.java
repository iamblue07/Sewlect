package com.sewlect.stylist.config;

import com.sewlect.common.logging.support.MdcTaskDecorator;
import com.sewlect.stylist.properties.StylistExecutorProperties;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@AllArgsConstructor
@EnableConfigurationProperties(StylistExecutorProperties.class)
public class StylistAsyncConfig {

    private final StylistExecutorProperties properties;

    @Bean
    public AsyncTaskExecutor stylistExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("stylist-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }
}
