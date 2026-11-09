package com.sewlect.outfit.controller;

import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.service.PromptGenerationQuotaService;
import com.sewlect.outfit.service.PromptOutfitGenerationService;
import com.sewlect.outfit.service.PromptRefinementService;
import com.sewlect.outfit.support.SlotEditTargetResolver;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PromptController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class PromptControllerSecurityTest {

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
    void generate_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"something casual\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void generate_validTokenUnderLimit_returns200WithMappedBody() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        when(promptOutfitGenerationService.generate(any(), any(), anyBoolean()))
                .thenReturn(List.of(new OutfitResponse(outfitId, null, new BigDecimal("99.99"), List.of(), null)));

        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"something casual for a Tuesday\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outfitId").value(outfitId.toString()))
                .andExpect(jsonPath("$[0].totalPrice").value(99.99));
    }

    @Test
    void generate_rateLimitExceeded_returns429() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new RateLimitExceededException("Rate limit exceeded for this operation - try again later"))
                .when(promptGenerationQuotaService).consumeGeneration(userId, 1);

        mockMvc.perform(post("/api/v1/outfits/prompt")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"something casual\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void refine_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/refine", UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"make it dressier\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refineEmptySlot_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/refine", UUID.randomUUID(), "ACCESSORY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"a brown leather belt\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refineEmptySlot_validTokenUnderLimit_resolvesTheEmptySlotAndDelegatesWithTheJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        SlotEditTarget target = new SlotEditTarget(outfitId, GarmentRole.ACCESSORY, null, List.of(),
                Set.of("Men", "Unisex"));
        when(slotEditTargetResolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY)).thenReturn(target);
        when(promptRefinementService.refine(target, userId, "a brown leather belt")).thenReturn(List.of(
                new AlternativeCandidateResponse(candidateId, "Leather Belt", "https://example.com/belt.jpg",
                        new BigDecimal("25.00"), breakdown("0.75"))));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/refine", outfitId, "ACCESSORY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"a brown leather belt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(candidateId.toString()))
                .andExpect(jsonPath("$[0].projectedBreakdown.finalScore").value(0.75));
    }

    @Test
    void refineEmptySlot_consumesTheSharedRefinementBudget() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        when(slotEditTargetResolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY)).thenReturn(
                new SlotEditTarget(outfitId, GarmentRole.ACCESSORY, null, List.of(), Set.of("Men", "Unisex")));
        when(promptRefinementService.refine(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/refine", outfitId, "ACCESSORY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"a belt\"}"))
                .andExpect(status().isOk());

        verify(promptGenerationQuotaService).consumeRefinement(userId);
    }

    @Test
    void refineEmptySlot_rateLimitExceeded_returns429AndNeverResolvesOrRefines() throws Exception {
        doThrow(new RateLimitExceededException("Rate limit exceeded for this operation - try again later"))
                .when(promptGenerationQuotaService).consumeRefinement(any());

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/refine", UUID.randomUUID(), "ACCESSORY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"a belt\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));

        verifyNoInteractions(slotEditTargetResolver, promptRefinementService);
    }

    @Test
    void refineEmptySlot_mandatoryRole_returns400() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(slotEditTargetResolver.forEmptySlot(outfitId, GarmentRole.FOOTWEAR))
                .thenThrow(new BadRequestException("Only an optional slot (OUTERWEAR, ACCESSORY) can be filled by adding, got FOOTWEAR"));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/refine", outfitId, "FOOTWEAR")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"sneakers\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(promptRefinementService);
    }

    private CompatibilityScoreBreakdown breakdown(String finalScore) {
        BigDecimal score = new BigDecimal(finalScore);
        return new CompatibilityScoreBreakdown(score, score, score, score, score);
    }
}
