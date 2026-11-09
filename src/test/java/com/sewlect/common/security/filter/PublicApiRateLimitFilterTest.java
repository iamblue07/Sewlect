package com.sewlect.common.security.filter;

import com.sewlect.common.exception.support.ErrorResponseFactory;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.common.security.properties.AuthRateLimitProperties;
import com.sewlect.common.security.properties.PublicApiRateLimitProperties;
import com.sewlect.common.security.support.RateLimitRejectionWriter;
import com.sewlect.common.security.support.RateLimitSubjectHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicApiRateLimitFilterTest {

    private static final String CLIENT_IP = "198.51.100.42";
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final PublicApiRateLimitProperties PROPERTIES = new PublicApiRateLimitProperties(60, WINDOW);
    private static final RateLimitSubjectHasher HASHER = new RateLimitSubjectHasher(
            new AuthRateLimitProperties(20, 10, Duration.ofMinutes(15), "test-rate-limit-secret-at-least-32-chars"));
    private static final String HASHED_CLIENT_IP = HASHER.hash(CLIENT_IP);

    @Mock
    private RateLimiter rateLimiter;

    private PublicApiRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        ErrorResponseFactory errorResponseFactory = new ErrorResponseFactory(
                Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC));
        filter = new PublicApiRateLimitFilter(rateLimiter, HASHER, PROPERTIES,
                new RateLimitRejectionWriter(errorResponseFactory, JsonMapper.builder().build()));
    }

    @Test
    void publicPathUnderBudget_consumesThePublicIpKeyAndContinuesTheChain() throws Exception {
        MockHttpServletRequest request = request("/api/v1/public/outfits/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(rateLimiter.tryConsume(HASHED_CLIENT_IP, PublicApiRateLimitFilter.IP_OPERATION_KEY, 60, WINDOW))
                .thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void publicPathOverBudget_returns429WithErrorBodyAndNeverReachesTheChain() throws Exception {
        MockHttpServletRequest request = request("/api/v1/public/outfits/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(rateLimiter.tryConsume(eq(HASHED_CLIENT_IP), eq(PublicApiRateLimitFilter.IP_OPERATION_KEY), anyInt(), eq(WINDOW)))
                .thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"status\":429");
        assertThat(response.getContentAsString()).contains("\"path\":\"/api/v1/public/outfits/123\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void nonPublicPath_isNeverRateLimited() throws Exception {
        for (String path : new String[]{"/api/v1/outfits/123", "/api/v1/auth/login", "/api/v1/publicity", "/api/v1/public"}) {
            MockHttpServletRequest request = request(path);
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isSameAs(request);
        }
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void rawClientIpNeverReachesTheLimiter_onlyItsHash() throws Exception {
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);

        filter.doFilterInternal(request("/api/v1/public/outfits/123"), new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).tryConsume(subjectCaptor.capture(), any(), anyInt(), any());
        assertThat(subjectCaptor.getValue()).isEqualTo(HASHED_CLIENT_IP);
        assertThat(subjectCaptor.getValue()).doesNotContain(CLIENT_IP);
        assertThat(subjectCaptor.getValue()).matches("[0-9a-f]{64}");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(CLIENT_IP);
        return request;
    }
}
