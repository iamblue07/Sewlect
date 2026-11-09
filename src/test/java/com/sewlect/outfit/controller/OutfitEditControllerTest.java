package com.sewlect.outfit.controller;

import com.sewlect.outfit.service.GarmentAlternativesService;
import com.sewlect.outfit.service.OutfitEditService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutfitEditController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class OutfitEditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlotEditTargetResolver slotEditTargetResolver;

    @MockitoBean
    private GarmentAlternativesService garmentAlternativesService;

    @MockitoBean
    private OutfitEditService outfitEditService;

    @Test
    void swap_missingProductIdField_returns400AndNeverInvokesService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitEditService);
    }

    @Test
    void swap_explicitNullProductId_returns400AndNeverInvokesService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {"productId": null}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitEditService);
    }

    @Test
    void swap_malformedProductIdValue_returns400() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        String body = """
                {"productId": "not-a-uuid"}
                """;

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(outfitEditService);
    }

    @Test
    void add_missingProductIdField_returns400AndNeverResolvesOrInvokesService() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/add", UUID.randomUUID(), "ACCESSORY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(slotEditTargetResolver, outfitEditService);
    }

    @Test
    void add_unknownRoleValue_returns400AndNeverResolvesOrInvokesService() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/add", UUID.randomUUID(), "HAT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": \"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(slotEditTargetResolver, outfitEditService);
    }

    @Test
    void slotAlternatives_lowercaseRole_returns400BecauseRoleMustBeUppercase() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/{outfitId}/slots/{role}/alternatives", UUID.randomUUID(), "accessory"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(slotEditTargetResolver, garmentAlternativesService);
    }

    @Test
    void remove_malformedItemId_returns400AndNeverResolves() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/remove", UUID.randomUUID(), "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(slotEditTargetResolver, outfitEditService);
    }
}
