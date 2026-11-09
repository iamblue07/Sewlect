package com.sewlect.support;

import com.sewlect.common.security.support.RateLimitRejectionWriter;
import com.sewlect.common.security.support.RateLimitSubjectHasher;
import com.sewlect.common.exception.support.ErrorResponseFactory;
import com.sewlect.common.ratelimit.InMemoryRateLimiter;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.common.security.config.JwtConfig;
import com.sewlect.common.security.config.SecurityConfig;
import com.sewlect.common.security.handler.RestAccessDeniedHandler;
import com.sewlect.common.security.handler.RestAuthenticationEntryPoint;
import com.sewlect.common.security.properties.AuthRateLimitProperties;
import com.sewlect.common.security.properties.CorsProperties;
import com.sewlect.common.security.properties.PublicApiRateLimitProperties;
import com.sewlect.identity.service.AppUserDetailsService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;

@TestConfiguration
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
public class WebSliceTestConfig {

    public static final String TEST_SUBJECT_HASH_SECRET = "test-rate-limit-secret-at-least-32-chars";
    public static final String TEST_JWT_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";
    public static final String TEST_ISSUER = "https://sewlect.local";
    public static final String TEST_AUDIENCE = "sewlect-api";

    public static final String JWT_SECRET_PROPERTY = "jwt.secret=" + TEST_JWT_SECRET;
    public static final String JWT_ACCESS_EXPIRATION_PROPERTY = "jwt.access-expiration=900000";
    public static final String JWT_REFRESH_EXPIRATION_PROPERTY = "jwt.refresh-expiration=604800000";
    public static final String SUBJECT_HASH_SECRET_PROPERTY =
            "auth.rate-limit.subject-hash-secret=" + TEST_SUBJECT_HASH_SECRET;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ErrorResponseFactory errorResponseFactory(Clock clock) {
        return new ErrorResponseFactory(clock);
    }

    @Bean
    public RateLimiter rateLimiter(Clock clock) {
        return new InMemoryRateLimiter(clock);
    }

    @Bean
    public AuthRateLimitProperties authRateLimitProperties() {
        return new AuthRateLimitProperties(20, 10, Duration.ofMinutes(15), TEST_SUBJECT_HASH_SECRET);
    }

    @Bean
    public RateLimitSubjectHasher rateLimitSubjectHasher(AuthRateLimitProperties authRateLimitProperties) {
        return new RateLimitSubjectHasher(authRateLimitProperties);
    }

    @Bean
    public PublicApiRateLimitProperties publicApiRateLimitProperties() {
        return new PublicApiRateLimitProperties(60, Duration.ofMinutes(1));
    }

    @Bean
    public RateLimitRejectionWriter rateLimitRejectionWriter(ErrorResponseFactory errorResponseFactory,
                                                             JsonMapper jsonMapper) {
        return new RateLimitRejectionWriter(errorResponseFactory, jsonMapper);
    }

    @Bean
    public CorsProperties corsProperties() {
        return new CorsProperties(List.of());
    }

    @Bean
    public AppUserDetailsService appUserDetailsService() {
        return mock(AppUserDetailsService.class);
    }

    public static String accessToken() {
        return accessToken(UUID.randomUUID(), "USER");
    }

    public static String accessToken(UUID userId) {
        return accessToken(userId, "USER");
    }

    public static String accessToken(UUID userId, String role) {
        SecretKey secretKey = new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(secretKey).build();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuer(TEST_ISSUER)
                .audience(List.of(TEST_AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("role", role)
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}