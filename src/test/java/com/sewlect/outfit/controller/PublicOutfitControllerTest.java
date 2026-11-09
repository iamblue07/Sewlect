package com.sewlect.outfit.controller;

import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.logging.filter.CorrelationIdFilter;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.dto.PublicOutfitItemResponse;
import com.sewlect.outfit.dto.PublicOutfitResponse;
import com.sewlect.outfit.service.PublicOutfitQueryService;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicOutfitController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class PublicOutfitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicOutfitQueryService publicOutfitQueryService;

    @Test
    void getOutfit_withoutAuthorizationHeader_returns200WithOnlyPublicFields() throws Exception {
        UUID outfitId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(publicOutfitQueryService.getPublicView(outfitId)).thenReturn(new PublicOutfitResponse(
                outfitId, new BigDecimal("129.99"), 4L,
                List.of(new PublicOutfitItemResponse(productId, "Slim Fit Tee", "https://example.com/tee.jpg",
                        new BigDecimal("39.99"), GarmentRole.TOP))));

        mockMvc.perform(get("/api/v1/public/outfits/{outfitId}", outfitId).with(fromIp("203.0.113.10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outfitId").value(outfitId.toString()))
                .andExpect(jsonPath("$.totalPrice").value(129.99))
                .andExpect(jsonPath("$.likeCount").value(4))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].slot").value("TOP"))
                .andExpect(jsonPath("$.compatibilityBreakdown").doesNotExist())
                .andExpect(jsonPath("$.engagement").doesNotExist())
                .andExpect(jsonPath("$.items[0].itemId").doesNotExist())
                .andExpect(jsonPath("$.items[0].removable").doesNotExist());
    }

    @Test
    void getOutfit_unknownId_returns404() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(publicOutfitQueryService.getPublicView(outfitId))
                .thenThrow(new ResourceNotFoundException("Outfit not found: " + outfitId));

        mockMvc.perform(get("/api/v1/public/outfits/{outfitId}", outfitId).with(fromIp("203.0.113.11")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getOutfit_sixtyFirstCallFromOneIpWithinTheWindow_returns429WithErrorBodyAndCorrelationId() throws Exception {
        UUID outfitId = UUID.randomUUID();
        when(publicOutfitQueryService.getPublicView(any()))
                .thenReturn(new PublicOutfitResponse(outfitId, BigDecimal.ZERO, 0L, List.of()));
        RequestPostProcessor sameIp = fromIp("203.0.113.12");

        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/v1/public/outfits/{outfitId}", outfitId).with(sameIp))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/public/outfits/{outfitId}", outfitId).with(sameIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.path").value("/api/v1/public/outfits/" + outfitId))
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));

        mockMvc.perform(get("/api/v1/public/outfits/{outfitId}", outfitId).with(fromIp("203.0.113.13")))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
