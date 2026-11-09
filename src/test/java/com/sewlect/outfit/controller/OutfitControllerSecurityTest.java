package com.sewlect.outfit.controller;

import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.domain.CompatibilityScoreBreakdown;
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
class OutfitControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OutfitItemQueryService outfitItemQueryService;

    @MockitoBean
    private OutfitResponseAssembler outfitResponseAssembler;

    @Test
    void getOutfit_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/outfits/{outfitId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void getOutfit_outfitGeneratedByAnotherUser_stillResolves200() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID generatingUserId = UUID.randomUUID();
        UUID viewingUserId = UUID.randomUUID();
        Outfit outfit = Outfit.builder().id(outfitId).build();
        OutfitResponse mockResponse = new OutfitResponse(outfitId,
                new CompatibilityScoreBreakdown(new BigDecimal("0.9"), new BigDecimal("0.8"),
                        new BigDecimal("0.85"), new BigDecimal("0.7"), new BigDecimal("0.82")),
                new BigDecimal("129.99"), List.of(), OutfitEngagement.none());

        when(outfitItemQueryService.getById(outfitId)).thenReturn(outfit);
        when(outfitResponseAssembler.toResponse(eq(outfit), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/outfits/{outfitId}", outfitId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + WebSliceTestConfig.accessToken(viewingUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(outfitId.toString()));

        mockMvc.perform(get("/api/v1/outfits/{outfitId}", outfitId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + WebSliceTestConfig.accessToken(generatingUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(outfitId.toString()));
    }
}