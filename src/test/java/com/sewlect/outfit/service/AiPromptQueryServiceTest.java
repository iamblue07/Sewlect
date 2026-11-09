package com.sewlect.outfit.service;

import com.sewlect.common.ai.properties.DeepInfraProperties;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.entity.AiPromptQuery;
import com.sewlect.outfit.entity.AiPromptQueryOutfit;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.enums.AiPromptQueryStatus;
import com.sewlect.outfit.repository.AiPromptQueryOutfitRepository;
import com.sewlect.outfit.repository.AiPromptQueryRepository;
import com.sewlect.outfit.repository.OutfitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPromptQueryServiceTest {

    private static final String CHAT_MODEL = "openai/gpt-oss-120b";

    @Mock
    private AiPromptQueryRepository aiPromptQueryRepository;

    @Mock
    private AiPromptQueryOutfitRepository aiPromptQueryOutfitRepository;

    @Mock
    private OutfitRepository outfitRepository;

    @Mock
    private UserReferenceQueryService userReferenceQueryService;

    private AiPromptQueryService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = serviceWithModel(CHAT_MODEL);
        when(userReferenceQueryService.getReference(userId)).thenReturn(User.builder().id(userId).build());
    }

    @Test
    void logSuccess_stampsTheConfiguredChatModelOnASuccessRow() {
        service.logSuccess(userId, "black jeans and white sneakers", Map.of("blueprints", List.of()), true, List.of());

        AiPromptQuery saved = captureSavedQuery();
        assertThat(saved.getModel()).isEqualTo(CHAT_MODEL);
        assertThat(saved.getStatus()).isEqualTo(AiPromptQueryStatus.SUCCESS);
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void logFailure_stampsTheConfiguredChatModelOnAFailedRow() {
        service.logFailure(userId, "something impossible", "DeepInfra prompt extraction failed: timeout");

        AiPromptQuery saved = captureSavedQuery();
        assertThat(saved.getModel()).isEqualTo(CHAT_MODEL);
        assertThat(saved.getStatus()).isEqualTo(AiPromptQueryStatus.FAILED);
        assertThat(saved.getErrorMessage()).isEqualTo("DeepInfra prompt extraction failed: timeout");
    }

    @Test
    void bothRowKinds_followTheConfiguredValueRatherThanAHardcodedOne() {
        AiPromptQueryService overridden = serviceWithModel("some-provider/another-model");

        overridden.logFailure(userId, "first", "boom");
        overridden.logSuccess(userId, "second", Map.of(), false, List.of());

        ArgumentCaptor<AiPromptQuery> captor = ArgumentCaptor.forClass(AiPromptQuery.class);
        verify(aiPromptQueryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AiPromptQuery::getModel)
                .containsOnly("some-provider/another-model");
    }

    @Test
    void logSuccess_linksEveryResultingOutfitInRankOrderInOneBatch() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(outfitRepository.getReferenceById(any())).thenAnswer(invocation ->
                Outfit.builder().id(invocation.getArgument(0)).build());

        service.logSuccess(userId, "office look", Map.of(), true, List.of(first, second));

        AiPromptQuery saved = captureSavedQuery();
        assertThat(saved.getModel()).isEqualTo(CHAT_MODEL);
        ArgumentCaptor<List<AiPromptQueryOutfit>> linksCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiPromptQueryOutfitRepository).saveAll(linksCaptor.capture());
        assertThat(linksCaptor.getValue()).extracting(AiPromptQueryOutfit::getRank).containsExactly(0, 1);
        assertThat(linksCaptor.getValue()).extracting(link -> link.getOutfit().getId()).containsExactly(first, second);
        verify(aiPromptQueryOutfitRepository, never()).save(any());
    }

    private AiPromptQuery captureSavedQuery() {
        ArgumentCaptor<AiPromptQuery> captor = ArgumentCaptor.forClass(AiPromptQuery.class);
        verify(aiPromptQueryRepository).save(captor.capture());
        return captor.getValue();
    }

    private AiPromptQueryService serviceWithModel(String chatModel) {
        DeepInfraProperties properties = new DeepInfraProperties(
                "https://api.deepinfra.test/v1/openai", "test-key", "Qwen/Qwen3-Embedding-4B",
                chatModel, "meta-llama/Llama-Guard-4-12B", "low", Duration.ofSeconds(60), Duration.ofSeconds(10), 1);
        return new AiPromptQueryService(aiPromptQueryRepository, aiPromptQueryOutfitRepository, outfitRepository,
                userReferenceQueryService, JsonMapper.builder().build(), properties);
    }
}
