package com.sewlect.common.ai.config;

import com.sewlect.common.ai.properties.DeepInfraProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(DeepInfraProperties.class)
public class DeepInfraConfig {

    public static final String PROVIDER = "deepinfra";

    private static final int GUARD_MAX_TOKENS = 20;

    private final DeepInfraProperties properties;

    public DeepInfraConfig(DeepInfraProperties properties) {
        this.properties = properties;
    }

    @Bean
    public OpenAiEmbeddingModel deepInfraEmbeddingModel() {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .model(properties.embeddingModel())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries())
                .build();
        return OpenAiEmbeddingModel.builder()
                .options(options)
                .build();
    }

    @Bean
    @Primary
    public OpenAiChatModel deepInfraChatModel() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .model(properties.chatModel())
                .reasoningEffort(properties.reasoningEffort())
                .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                        .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                        .build())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries())
                .build();
        return OpenAiChatModel.builder()
                .options(options)
                .build();
    }

    @Bean
    public OpenAiChatModel deepInfraGuardModel() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .model(properties.guardModel())
                .temperature(0.0)
                .maxTokens(GUARD_MAX_TOKENS)
                .timeout(properties.guardTimeout())
                .maxRetries(properties.maxRetries())
                .build();
        return OpenAiChatModel.builder()
                .options(options)
                .build();
    }
}
