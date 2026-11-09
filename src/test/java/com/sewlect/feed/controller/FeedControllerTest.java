package com.sewlect.feed.controller;

import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.feed.properties.FeedProperties;
import com.sewlect.feed.service.FeedGenerationService;
import com.sewlect.feed.support.FeedResponseAssembler;
import com.sewlect.support.WebSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedGenerationService feedGenerationService;

    @MockitoBean
    private FeedResponseAssembler feedResponseAssembler;

    @MockitoBean
    private FeedProperties feedProperties;

    @Test
    void reset_missingAuthorizationHeader_returns401AndNeverResets() throws Exception {
        mockMvc.perform(post("/api/v1/feed/reset"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(feedGenerationService);
    }

    @Test
    void reset_validToken_returns204WithNoBodyAndResetsTheJwtSubjectsFeed() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/feed/reset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(feedGenerationService).reset(userId);
    }

    @Test
    void reset_resetBudgetExhausted_returns429() throws Exception {
        doThrow(new RateLimitExceededException("Feed reset limit exceeded - try again later"))
                .when(feedGenerationService).reset(any());

        mockMvc.perform(post("/api/v1/feed/reset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }
}
