package com.sewlect.social.controller;

import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.social.dto.InteractionStateResponse;
import com.sewlect.social.dto.SavedOutfitsResponse;
import com.sewlect.social.enums.InteractionType;
import com.sewlect.social.service.OutfitInteractionService;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
class OutfitInteractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OutfitInteractionService outfitInteractionService;

    @Test
    void like_returnsInteractionStateResponseFromService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.activate(any(), eq(outfitId), eq(InteractionType.LIKE)))
                .thenReturn(new InteractionStateResponse(true, OutfitEngagement.none()));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/like", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unlike_returnsInteractionStateResponseFromService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.deactivate(any(), eq(outfitId), eq(InteractionType.LIKE)))
                .thenReturn(new InteractionStateResponse(false, OutfitEngagement.none()));

        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/like", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void save_returnsInteractionStateResponseFromService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.activate(any(), eq(outfitId), eq(InteractionType.SAVE)))
                .thenReturn(new InteractionStateResponse(true, OutfitEngagement.none()));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/save", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unsave_returnsInteractionStateResponseFromService() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitInteractionService.deactivate(any(), eq(outfitId), eq(InteractionType.SAVE)))
                .thenReturn(new InteractionStateResponse(false, OutfitEngagement.none()));

        mockMvc.perform(delete("/api/v1/outfits/{outfitId}/save", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void like_malformedOutfitIdInPath_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/like", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitInteractionService);
    }

    @Test
    void listSaved_defaultPageAndSize_usesPageZeroAndTheConfiguredDefaultSize() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 20);
    }

    @Test
    void listSaved_sizeZero_clampsToTheConfiguredDefaultSize() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 20);
    }

    @Test
    void listSaved_negativeSize_clampsToTheConfiguredDefaultSize() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("size", "-5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 20);
    }

    @Test
    void listSaved_sizeAboveMax_clampsToTheConfiguredMaximum() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("size", "5000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 100);
    }

    @Test
    void listSaved_sizeExactlyAtMax_staysUnclamped() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 100);
    }

    @Test
    void listSaved_negativePage_clampsToZero() throws Exception {
        stubEmptySavedPage();

        mockMvc.perform(get("/api/v1/outfits/saved").param("page", "-3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk());

        assertPageable(0, 20);
    }

    @Test
    void listInteractedOutfitIds_missingTypeParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/interactions/mine")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitInteractionService);
    }

    @Test
    void listInteractedOutfitIds_invalidTypeValue_returns400NotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "SHARE_NOW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitInteractionService);
    }

    @Test
    void listInteractedOutfitIds_validType_returnsSetFromService() throws Exception {
        UUID likedOutfitId = UUID.randomUUID();
        when(outfitInteractionService.listInteractedOutfitIds(any(), eq(InteractionType.LIKE)))
                .thenReturn(Set.of(likedOutfitId));

        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "LIKE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(likedOutfitId.toString()));
    }

    @Test
    void listInteractedOutfitIds_shareType_isAcceptedAndReturnsAnEmptySet() throws Exception {
        when(outfitInteractionService.listInteractedOutfitIds(any(), eq(InteractionType.SHARE)))
                .thenReturn(Set.of());

        mockMvc.perform(get("/api/v1/outfits/interactions/mine").param("type", "SHARE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void stubEmptySavedPage() {
        when(outfitInteractionService.listSaved(any(), any()))
                .thenReturn(new SavedOutfitsResponse(List.of(), 0, 20, 0L, 0));
    }

    private void assertPageable(int expectedPage, int expectedSize) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(outfitInteractionService).listSaved(any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(expectedPage);
        assertThat(captor.getValue().getPageSize()).isEqualTo(expectedSize);
    }
}