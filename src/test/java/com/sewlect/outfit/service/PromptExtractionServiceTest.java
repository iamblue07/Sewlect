package com.sewlect.outfit.service;

import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.common.logging.support.ExternalCallLogger;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.properties.OutfitPromptProperties;
import com.sewlect.outfit.domain.StructuredPromptQuery;
import com.sewlect.outfit.support.OutfitShapeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptExtractionServiceTest {

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private ExternalCallLogger externalCallLogger;

    private PromptExtractionService service;

    @BeforeEach
    void setUp() {
        OutfitPromptProperties properties = new OutfitPromptProperties(2, 3, 200, 200, 50);
        service = new PromptExtractionService(
                chatModel, properties,
                externalCallLogger, new OutfitShapeValidator());
        lenient().when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
    }

    @Test
    void extract_validTopBottomBlueprint_parsesSuccessfully() {
        stubChatResponse("""
                {"blueprints":[{"slots":[
                    {"role":"TOP","description":"a light blue linen shirt"},
                    {"role":"BOTTOM","description":"beige chino trousers"},
                    {"role":"FOOTWEAR","description":"white leather sneakers"}
                ]}]}
                """);

        StructuredPromptQuery result = service.extract("something breezy for a summer lunch");

        assertThat(result.blueprints()).hasSize(1);
        assertThat(result.blueprints().get(0).slots()).extracting("role")
                .containsExactly(GarmentRole.TOP, GarmentRole.BOTTOM, GarmentRole.FOOTWEAR);
    }

    @Test
    void extract_validFullBodyBlueprint_parsesSuccessfully() {
        stubChatResponse("""
                {"blueprints":[{"slots":[
                    {"role":"FULL_BODY","description":"a black cocktail dress"},
                    {"role":"FOOTWEAR","description":"black heeled sandals"}
                ]}]}
                """);

        StructuredPromptQuery result = service.extract("something elegant for a dinner party");

        assertThat(result.blueprints().get(0).slots()).extracting("role")
                .containsExactly(GarmentRole.FULL_BODY, GarmentRole.FOOTWEAR);
    }

    @Test
    void extract_multipleBlueprints_parsesAllOfThem() {
        stubChatResponse("""
                {"blueprints":[
                    {"slots":[{"role":"TOP","description":"a shirt"},{"role":"BOTTOM","description":"trousers"},{"role":"FOOTWEAR","description":"shoes"}]},
                    {"slots":[{"role":"FULL_BODY","description":"a jumpsuit"},{"role":"FOOTWEAR","description":"boots"}]}
                ]}
                """);

        StructuredPromptQuery result = service.extract("something for a garden party");

        assertThat(result.blueprints()).hasSize(2);
    }

    @Test
    void extract_blueprintCombiningTopBottomAndFullBody_throwsExternalServiceException() {
        stubChatResponse("""
                {"blueprints":[{"slots":[
                    {"role":"TOP","description":"a shirt"},
                    {"role":"BOTTOM","description":"trousers"},
                    {"role":"FULL_BODY","description":"a dress"},
                    {"role":"FOOTWEAR","description":"shoes"}
                ]}]}
                """);

        assertThatThrownBy(() -> service.extract("give me something contradictory"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("shape rule");
    }

    @Test
    void extract_blueprintWithOnlyTopNoBottom_throwsExternalServiceException() {
        stubChatResponse("""
                {"blueprints":[{"slots":[
                    {"role":"TOP","description":"a shirt"},
                    {"role":"FOOTWEAR","description":"shoes"}
                ]}]}
                """);

        assertThatThrownBy(() -> service.extract("just a top somehow"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("shape rule");
    }

    @Test
    void extract_blueprintMissingFootwear_throwsExternalServiceException() {
        stubChatResponse("""
                {"blueprints":[{"slots":[
                    {"role":"TOP","description":"a shirt"},
                    {"role":"BOTTOM","description":"trousers"}
                ]}]}
                """);

        assertThatThrownBy(() -> service.extract("something with no shoes mentioned"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("footwear");
    }

    @Test
    void extract_blueprintWithDuplicateRoles_throwsExternalServiceException() {
        stubChatResponse("""
                {"blueprints":[{"slots":[
                    {"role":"TOP","description":"a shirt"},
                    {"role":"TOP","description":"another shirt"},
                    {"role":"BOTTOM","description":"trousers"},
                    {"role":"FOOTWEAR","description":"shoes"}
                ]}]}
                """);

        assertThatThrownBy(() -> service.extract("give me two tops somehow"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void extract_emptyBlueprintList_throwsExternalServiceException() {
        stubChatResponse("""
                {"blueprints":[]}
                """);

        assertThatThrownBy(() -> service.extract("something impossible"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("no usable blueprints");
    }

    @Test
    void extract_malformedJson_throwsExternalServiceException() {
        stubChatResponse("this is not json at all, sorry");

        assertThatThrownBy(() -> service.extract("break the parser please"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("DeepInfra prompt extraction failed");
    }

    @Test
    void extract_chatModelThrows_wrapsInExternalServiceException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> service.extract("network is down"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    void extractSingleSlot_validResponse_returnsDescription() {
        stubChatResponse("""
                {"role":"TOP","description":"a sharper tailored blazer"}
                """);

        String description = service.extractSingleSlot("make it dressier", GarmentRole.TOP);

        assertThat(description).isEqualTo("a sharper tailored blazer");
    }

    @Test
    void extractSingleSlot_blankDescription_throwsExternalServiceException() {
        stubChatResponse("""
                {"role":"TOP","description":""}
                """);

        assertThatThrownBy(() -> service.extractSingleSlot("make it dressier", GarmentRole.TOP))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("no usable description");
    }

    @Test
    void extractSingleSlot_malformedJson_throwsExternalServiceException() {
        stubChatResponse("not json");

        assertThatThrownBy(() -> service.extractSingleSlot("make it dressier", GarmentRole.TOP))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("single-slot prompt extraction failed");
    }

    private void stubChatResponse(String text) {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        lenient().when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }
}