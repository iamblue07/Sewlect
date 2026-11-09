package com.sewlect.social.controller;

import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.social.dto.InteractionStateResponse;
import com.sewlect.social.dto.SavedOutfitsResponse;
import com.sewlect.social.enums.InteractionType;
import com.sewlect.social.service.OutfitInteractionService;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutfitInteractionController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class OutfitInteractionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OutfitInteractionService outfitInteractionService;

    @Test
    void like_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/like", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void like_validToken_isAuthorized() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.activate(any(), eq(outfitId), eq(InteractionType.LIKE)))
                .thenReturn(new InteractionStateResponse(true, OutfitEngagement.none()));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/like", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unlike_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/like", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unlike_validToken_isAuthorized() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.deactivate(any(), eq(outfitId), eq(InteractionType.LIKE)))
                .thenReturn(new InteractionStateResponse(false, OutfitEngagement.none()));

        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/like", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void save_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/save", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void save_validToken_isAuthorized() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.activate(any(), eq(outfitId), eq(InteractionType.SAVE)))
                .thenReturn(new InteractionStateResponse(true, OutfitEngagement.none()));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/save", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unsave_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/save", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unsave_validToken_isAuthorized() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.deactivate(any(), eq(outfitId), eq(InteractionType.SAVE)))
                .thenReturn(new InteractionStateResponse(false, OutfitEngagement.none()));

        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/save", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void listSaved_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/saved"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void listSaved_validToken_isAuthorized() throws Exception {
        when(outfitInteractionService.listSaved(any(), any()))
                .thenReturn(new SavedOutfitsResponse(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/v1/outfits/saved")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listInteractedOutfitIds_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "LIKE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void listInteractedOutfitIds_validToken_isAuthorized() throws Exception {
        UUID likedOutfitId = UUID.randomUUID();
        when(outfitInteractionService.listInteractedOutfitIds(any(), eq(InteractionType.LIKE)))
                .thenReturn(Set.of(likedOutfitId));

        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "LIKE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(likedOutfitId.toString()));
    }
}