package com.sewlect.outfit.controller;

import com.sewlect.outfit.service.PromptGenerationQuotaService;
import com.sewlect.outfit.service.PromptOutfitGenerationService;
import com.sewlect.outfit.service.PromptRefinementService;
import com.sewlect.outfit.support.SlotEditTargetResolver;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PromptController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class PromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PromptOutfitGenerationService promptOutfitGenerationService;

    @MockitoBean
    private PromptRefinementService promptRefinementService;

    @MockitoBean
    private PromptGenerationQuotaService promptGenerationQuotaService;

    @MockitoBean
    private SlotEditTargetResolver slotEditTargetResolver;

    @Test
    void generate_blankPrompt_returns400AndNeverInvokesService() throws Exception {
        String body = """
                {"prompt": ""}
                """;

        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(promptOutfitGenerationService);
    }

    @Test
    void generate_missingPromptField_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(promptOutfitGenerationService);
    }

    @Test
    void refine_blankPrompt_returns400AndNeverInvokesService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {"prompt": ""}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/refine", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(promptRefinementService);
    }

    @Test
    void refineEmptySlot_blankPrompt_returns400AndNeverConsumesRateLimitOrRefines() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/refine", UUID.randomUUID(), "ACCESSORY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(promptGenerationQuotaService, slotEditTargetResolver, promptRefinementService);
    }

    @Test
    void refineEmptySlot_unknownRoleValue_returns400AndNeverConsumesRateLimitOrRefines() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/refine", UUID.randomUUID(), "accessory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"a belt\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(promptGenerationQuotaService, slotEditTargetResolver, promptRefinementService);
    }
}
