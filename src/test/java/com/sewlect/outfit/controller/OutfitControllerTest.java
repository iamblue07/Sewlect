package com.sewlect.outfit.controller;

import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitItemQueryService;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OutfitController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class OutfitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OutfitItemQueryService outfitItemQueryService;

    @MockitoBean
    private OutfitResponseAssembler outfitResponseAssembler;

    @Test
    void getOutfit_existingId_returnsOutfitResponse() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Outfit outfit = Outfit.builder().id(outfitId).build();
        OutfitResponse mockResponse = new OutfitResponse(outfitId,
                new CompatibilityScoreBreakdown(new BigDecimal("0.9"), new BigDecimal("0.8"),
                        new BigDecimal("0.85"), new BigDecimal("0.7"), new BigDecimal("0.82")),
                new BigDecimal("129.99"),
                List.of(new OutfitItemView(itemId, productId, "Slim Fit Tee", "https://example.com/tee.jpg",
                        new BigDecimal("39.99"), GarmentRole.TOP, false)),
                OutfitEngagement.none());

        when(outfitItemQueryService.getById(outfitId)).thenReturn(outfit);
        when(outfitResponseAssembler.toResponse(eq(outfit), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/outfits/{outfitId}", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(outfitId.toString()))
                .andExpect(jsonPath("$.totalPrice").value(129.99))
                .andExpect(jsonPath("$.compatibilityBreakdown.finalScore").value(0.82))
                .andExpect(jsonPath("$.items[0].itemId").value(itemId.toString()))
                .andExpect(jsonPath("$.items[0].slot").value("TOP"));
    }

    @Test
    void getOutfit_nonExistentId_returns404() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(outfitItemQueryService.getById(outfitId))
                .thenThrow(new ResourceNotFoundException("Outfit not found: " + outfitId));

        mockMvc.perform(get("/api/v1/outfits/{outfitId}", outfitId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        verifyNoInteractions(outfitResponseAssembler);
    }

    @Test
    void getOutfit_malformedUuidInPath_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/{outfitId}", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(outfitItemQueryService);
        verifyNoInteractions(outfitResponseAssembler);
    }
}