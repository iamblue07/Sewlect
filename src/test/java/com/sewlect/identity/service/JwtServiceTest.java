package com.sewlect.identity.service;

import com.sewlect.common.security.config.JwtConfig;
import com.sewlect.common.security.properties.JwtProperties;
import com.sewlect.identity.enums.Role;
import com.sewlect.identity.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-at-least-32-characters-long-xxxx";
    private static final String TEST_ISSUER = "https://sewlect.local";
    private static final String TEST_AUDIENCE = "sewlect-api";

    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                TEST_SECRET, Duration.ofMinutes(15), Duration.ofDays(7), TEST_ISSUER, TEST_AUDIENCE);

        JwtConfig jwtConfig = new JwtConfig(jwtProperties);
        jwtEncoder = jwtConfig.jwtEncoder();
        jwtDecoder = jwtConfig.jwtDecoder();

        jwtService = new JwtService(jwtEncoder, jwtProperties, Clock.systemUTC());
    }

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("jane@example.com")
                .passwordHash("irrelevant-for-this-test")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    void generateAccessToken_containsSubIssuerAudienceAndRoleClaims() {
        User user = buildUser();

        String token = jwtService.generateAccessToken(user);
        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getClaimAsString(JwtClaimNames.ISS)).isEqualTo(TEST_ISSUER);
        assertThat(decoded.getAudience()).containsExactly(TEST_AUDIENCE);
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ADMIN");
    }

    @Test
    void generateAccessToken_doesNotCarryTheUsersEmailAddress() {
        User user = buildUser();

        String token = jwtService.generateAccessToken(user);
        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getClaims()).doesNotContainKey("email");
        assertThat(decoded.getClaims().toString()).doesNotContain("jane@example.com");
    }

    @Test
    void generateAccessToken_expiresInConfiguredFifteenMinutes() {
        User user = buildUser();
        Instant beforeGeneration = Instant.now();

        String token = jwtService.generateAccessToken(user);
        Jwt decoded = jwtDecoder.decode(token);

        Instant expectedExpiry = beforeGeneration.plus(Duration.ofMinutes(15));
        assertThat(decoded.getExpiresAt()).isCloseTo(expectedExpiry, within(2, ChronoUnit.SECONDS));
    }

    @Test
    void decode_tokenSignedWithThisSecretButMintedForAnotherAudience_isRejected() {
        String foreignAudienceToken = mintToken(TEST_ISSUER, "some-other-service");

        assertThatThrownBy(() -> jwtDecoder.decode(foreignAudienceToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void generateRefreshToken_producesSufficientlyRandomValueOfExpectedLength() {
        String token1 = jwtService.generateRefreshToken();
        String token2 = jwtService.generateRefreshToken();

        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1).matches("^[A-Za-z0-9_-]+$");
        assertThat(Base64.getUrlDecoder().decode(token1)).hasSize(32);
    }

    @Test
    void hashToken_isDeterministic() {
        String rawToken = "some-raw-refresh-token-value";

        String hash1 = jwtService.hashToken(rawToken);
        String hash2 = jwtService.hashToken(rawToken);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).matches("^[0-9a-f]{64}$");
    }

    private String mintToken(String issuer, String audience) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("role", "USER")
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}