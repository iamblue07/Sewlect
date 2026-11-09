package com.sewlect.outfit.controller;

import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ConflictException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.domain.SlotEditTarget;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.service.GarmentAlternativesService;
import com.sewlect.outfit.service.OutfitEditService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutfitEditController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class OutfitEditControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SlotEditTargetResolver slotEditTargetResolver;

    @MockitoBean
    private GarmentAlternativesService garmentAlternativesService;

    @MockitoBean
    private OutfitEditService outfitEditService;

    @Test
    void listAlternatives_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/{outfitId}/items/{itemId}/alternatives",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void listAlternatives_validToken_returns200WithMappedBodyAndDerivesUserIdFromJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        List<AlternativeCandidateResponse> mockResponse = List.of(
                new AlternativeCandidateResponse(candidateId, "Slim Fit Tee", "https://example.com/tee.jpg",
                        new BigDecimal("39.99"), breakdown("0.82")));
        SlotEditTarget target = targetFor(outfitId);
        when(slotEditTargetResolver.forItem(outfitId, itemId)).thenReturn(target);
        when(garmentAlternativesService.listAlternatives(target, userId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/outfits/{outfitId}/items/{itemId}/alternatives", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(candidateId.toString()))
                .andExpect(jsonPath("$[0].productDisplayName").value("Slim Fit Tee"))
                .andExpect(jsonPath("$[0].basePrice").value(39.99))
                .andExpect(jsonPath("$[0].projectedBreakdown.finalScore").value(0.82));
    }

    @Test
    void listAlternatives_itemNotInOutfit_returns404() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(slotEditTargetResolver.forItem(outfitId, itemId))
                .thenThrow(new ResourceNotFoundException("Outfit item " + itemId + " not found in outfit " + outfitId));

        mockMvc.perform(get("/api/v1/outfits/{outfitId}/items/{itemId}/alternatives", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verifyNoInteractions(garmentAlternativesService);
    }

    @Test
    void swap_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap",
                        UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void swap_validTokenAndBody_returns200WithMappedOutfitAndDerivesUserIdFromJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID resultOutfitId = UUID.randomUUID();
        UUID resultItemId = UUID.randomUUID();
        OutfitResponse mockResponse = new OutfitResponse(resultOutfitId, breakdown("0.68"), new BigDecimal("104.99"),
                List.of(new OutfitItemView(resultItemId, productId, "Slim Fit Tee", "https://example.com/tee.jpg",
                        new BigDecimal("39.99"), GarmentRole.TOP, false)),
                new OutfitEngagement(3L, true, false));
        SlotEditTarget target = targetFor(outfitId);
        when(slotEditTargetResolver.forItem(outfitId, itemId)).thenReturn(target);
        when(outfitEditService.swap(target, productId, userId)).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(resultOutfitId.toString()))
                .andExpect(jsonPath("$.totalPrice").value(104.99))
                .andExpect(jsonPath("$.items[0].itemId").value(resultItemId.toString()))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].slot").value("TOP"))
                .andExpect(jsonPath("$.items[0].removable").value(false))
                .andExpect(jsonPath("$.engagement.likeCount").value(3))
                .andExpect(jsonPath("$.engagement.likedByViewer").value(true));
    }

    @Test
    void swap_serviceRejectsTheCandidate_returns400() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        SlotEditTarget target = targetFor(outfitId);
        when(slotEditTargetResolver.forItem(outfitId, itemId)).thenReturn(target);
        when(outfitEditService.swap(eq(target), any(), any()))
                .thenThrow(new BadRequestException("Product has garment role FOOTWEAR, but slot ACCESSORY was targeted"));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/swap", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void remove_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/remove",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void remove_validToken_resolvesTheItemAndDelegatesWithTheJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID resultOutfitId = UUID.randomUUID();
        SlotEditTarget target = targetFor(outfitId);
        when(slotEditTargetResolver.forItem(outfitId, itemId)).thenReturn(target);
        when(outfitEditService.remove(target, userId)).thenReturn(
                new OutfitResponse(resultOutfitId, breakdown("0.7"), new BigDecimal("90.00"), List.of(),
                        OutfitEngagement.none()));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/remove", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(resultOutfitId.toString()));

        verify(outfitEditService).remove(target, userId);
    }

    @Test
    void remove_mandatoryRole_returns400() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        SlotEditTarget target = targetFor(outfitId);
        when(slotEditTargetResolver.forItem(outfitId, itemId)).thenReturn(target);
        when(outfitEditService.remove(eq(target), any()))
                .thenThrow(new BadRequestException("Only an existing OUTERWEAR or ACCESSORY item can be removed"));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/items/{itemId}/remove", outfitId, itemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void slotAlternatives_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/{outfitId}/slots/{role}/alternatives", UUID.randomUUID(), "ACCESSORY"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void slotAlternatives_validToken_resolvesTheEmptySlotAndDelegatesWithTheJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        SlotEditTarget target = emptyAccessoryTargetFor(outfitId);
        when(slotEditTargetResolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY)).thenReturn(target);
        when(garmentAlternativesService.listAlternatives(target, userId)).thenReturn(List.of(
                new AlternativeCandidateResponse(candidateId, "Leather Belt", "https://example.com/belt.jpg",
                        new BigDecimal("25.00"), breakdown("0.75"))));

        mockMvc.perform(get("/api/v1/outfits/{outfitId}/slots/{role}/alternatives", outfitId, "ACCESSORY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(candidateId.toString()))
                .andExpect(jsonPath("$[0].projectedBreakdown.finalScore").value(0.75));
    }

    @Test
    void slotAlternatives_mandatoryRole_returns400() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(slotEditTargetResolver.forEmptySlot(outfitId, GarmentRole.TOP))
                .thenThrow(new BadRequestException("Only an optional slot (OUTERWEAR, ACCESSORY) can be filled by adding, got TOP"));

        mockMvc.perform(get("/api/v1/outfits/{outfitId}/slots/{role}/alternatives", outfitId, "TOP")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(garmentAlternativesService);
    }

    @Test
    void add_missingAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/add", UUID.randomUUID(), "ACCESSORY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void add_validTokenAndBody_resolvesTheEmptySlotAndDelegatesWithTheJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID resultOutfitId = UUID.randomUUID();
        SlotEditTarget target = emptyAccessoryTargetFor(outfitId);
        when(slotEditTargetResolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY)).thenReturn(target);
        when(outfitEditService.add(target, productId, userId)).thenReturn(
                new OutfitResponse(resultOutfitId, breakdown("0.8"), new BigDecimal("140.00"), List.of(),
                        OutfitEngagement.none()));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/add", outfitId, "ACCESSORY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(resultOutfitId.toString()))
                .andExpect(jsonPath("$.totalPrice").value(140.00));
    }

    @Test
    void add_slotAlreadyOccupied_returns409() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(slotEditTargetResolver.forEmptySlot(outfitId, GarmentRole.ACCESSORY))
                .thenThrow(new ConflictException("Outfit already has an item in slot ACCESSORY; swap it instead"));

        mockMvc.perform(post("/api/v1/outfits/{outfitId}/slots/{role}/add", outfitId, "ACCESSORY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        verifyNoInteractions(outfitEditService);
    }

    private SlotEditTarget targetFor(UUID outfitId) {
        return new SlotEditTarget(outfitId, GarmentRole.TOP, null, List.of(), Set.of("Men", "Unisex"));
    }

    private SlotEditTarget emptyAccessoryTargetFor(UUID outfitId) {
        return new SlotEditTarget(outfitId, GarmentRole.ACCESSORY, null, List.of(), Set.of("Men", "Unisex"));
    }

    private String productBody(UUID productId) {
        return """
                {"productId": "%s"}
                """.formatted(productId);
    }

    private CompatibilityScoreBreakdown breakdown(String finalScore) {
        BigDecimal score = new BigDecimal(finalScore);
        return new CompatibilityScoreBreakdown(score, score, score, score, score);
    }
}
