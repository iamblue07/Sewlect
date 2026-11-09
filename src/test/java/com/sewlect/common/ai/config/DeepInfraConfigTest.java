package com.sewlect.common.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DeepInfraConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DeepInfraConfig.class)
            .withPropertyValues(
                    "deepinfra.base-url=https://api.deepinfra.test/v1/openai",
                    "deepinfra.api-key=test-key",
                    "deepinfra.embedding-model=Qwen/Qwen3-Embedding-4B",
                    "deepinfra.chat-model=openai/gpt-oss-120b",
                    "deepinfra.guard-model=meta-llama/Llama-Guard-4-12B",
                    "deepinfra.reasoning-effort=low",
                    "deepinfra.timeout=45s",
                    "deepinfra.guard-timeout=8s",
                    "deepinfra.max-retries=2");

    @Test
    void context_declaresThePrimaryChatModelTheGuardModelAndOneEmbeddingModel() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(OpenAiChatModel.class))
                    .containsOnlyKeys("deepInfraChatModel", "deepInfraGuardModel");
            assertThat(context.getBean(OpenAiChatModel.class)).isSameAs(context.getBean("deepInfraChatModel"));
            assertThat(context).hasSingleBean(OpenAiEmbeddingModel.class);
            assertThat(context).hasBean("deepInfraEmbeddingModel");
        });
    }

    @Test
    void chatModel_carriesTheConfiguredModelReasoningEffortTimeoutAndRetries() {
        contextRunner.run(context -> {
            OpenAiChatOptions options = context.getBean(OpenAiChatModel.class).getOptions();

            assertThat(options.getBaseUrl()).isEqualTo("https://api.deepinfra.test/v1/openai");
            assertThat(options.getModel()).isEqualTo("openai/gpt-oss-120b");
            assertThat(options.getReasoningEffort()).isEqualTo("low");
            assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(45));
            assertThat(options.getMaxRetries()).isEqualTo(2);
        });
    }

    @Test
    void chatModel_defaultsToTheJsonObjectResponseFormat() {
        contextRunner.run(context -> {
            OpenAiChatOptions options = context.getBean(OpenAiChatModel.class).getOptions();

            assertThat(options.getResponseFormat()).isNotNull();
            assertThat(options.getResponseFormat().getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
        });
    }

    @Test
    void embeddingModel_carriesTheConfiguredModelTimeoutAndRetries() {
        contextRunner.run(context -> {
            OpenAiEmbeddingOptions options = context.getBean(OpenAiEmbeddingModel.class).getOptions();

            assertThat(options.getBaseUrl()).isEqualTo("https://api.deepinfra.test/v1/openai");
            assertThat(options.getModel()).isEqualTo("Qwen/Qwen3-Embedding-4B");
            assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(45));
            assertThat(options.getMaxRetries()).isEqualTo(2);
        });
    }

    @Test
    void reasoningEffortOutsideLowMediumHigh_failsStartup() {
        contextRunner.withPropertyValues("deepinfra.reasoning-effort=extreme")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingChatModel_failsStartup() {
        contextRunner.withPropertyValues("deepinfra.chat-model=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void guardModel_usesTheGuardModelWithDeterministicShortPlainTextOutput() {
        contextRunner.run(context -> {
            OpenAiChatOptions options = ((OpenAiChatModel) context.getBean("deepInfraGuardModel")).getOptions();

            assertThat(options.getBaseUrl()).isEqualTo("https://api.deepinfra.test/v1/openai");
            assertThat(options.getModel()).isEqualTo("meta-llama/Llama-Guard-4-12B");
            assertThat(options.getTemperature()).isEqualTo(0.0);
            assertThat(options.getMaxTokens()).isEqualTo(20);
            assertThat(options.getResponseFormat()).isNull();
            assertThat(options.getReasoningEffort()).isNull();
            assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(8));
            assertThat(options.getMaxRetries()).isEqualTo(2);
        });
    }

    @Test
    void missingGuardTimeout_failsStartup() {
        contextRunner.withPropertyValues("deepinfra.guard-timeout=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingGuardModel_failsStartup() {
        contextRunner.withPropertyValues("deepinfra.guard-model=")
                .run(context -> assertThat(context).hasFailed());
    }
}
