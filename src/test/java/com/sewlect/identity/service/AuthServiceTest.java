package com.sewlect.identity.service;

import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.identity.properties.PasswordChangeProperties;
import com.sewlect.common.exception.ConflictException;
import com.sewlect.identity.dto.ChangePasswordRequest;
import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.UnauthorizedException;
import com.sewlect.common.logging.support.SecurityEventLogger;
import com.sewlect.common.security.properties.JwtProperties;
import com.sewlect.identity.dto.AuthResponse;
import com.sewlect.identity.dto.LoginRequest;
import com.sewlect.identity.dto.LogoutRequest;
import com.sewlect.identity.dto.RefreshRequest;
import com.sewlect.identity.dto.RegisterRequest;
import com.sewlect.identity.entity.RefreshToken;
import com.sewlect.identity.enums.Role;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.entity.UserProfile;
import com.sewlect.identity.repository.RefreshTokenRepository;
import com.sewlect.identity.repository.UserProfileRepository;
import com.sewlect.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private SecurityEventLogger securityEventLogger;

    @Mock
    private RateLimiter rateLimiter;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "unused-by-authservice-directly", Duration.ofMinutes(15), Duration.ofDays(7),
                "https://sewlect.local", "sewlect-api");
        authService = new AuthService(userRepository, userProfileRepository, refreshTokenRepository,
                passwordEncoder, authenticationManager, jwtService, jwtProperties, securityEventLogger,
                rateLimiter, new PasswordChangeProperties(5, Duration.ofMinutes(15)), Clock.systemDefaultZone());
    }

    private User buildUser(String email, Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("existing-hash")
                .role(role)
                .build();
    }

    private RefreshToken buildRefreshToken(User owner, String tokenHash, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
    }

    private void stubTokenIssuance() {
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken("raw-refresh-token")).thenReturn("refresh-token-hash");
    }

    // ---- register ----

    @Test
    void register_newEmail_createsUserAndProfileInOneTransaction() {
        RegisterRequest request = new RegisterRequest("new@example.com", "password123");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubTokenIssuance();

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getUser()).isEqualTo(savedUser);
    }

    @Test
    void register_success_auditsRegistrationWithThePersistedUserId() {
        RegisterRequest request = new RegisterRequest("new@example.com", "password123");
        User persisted = buildUser("new@example.com", Role.USER);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(persisted);
        stubTokenIssuance();

        authService.register(request);

        verify(securityEventLogger).registrationSucceeded(persisted.getId());
        verify(securityEventLogger, never()).loginFailed();
    }

    @Test
    void register_duplicateEmail_throwsConflictExceptionAndAuditsNothing() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "password123");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(securityEventLogger);
    }

    @Test
    void register_concurrentRegistrationLosesUniqueConstraintRace_throwsConflictExceptionNotDataIntegrityViolationException() {
        RegisterRequest request = new RegisterRequest("racing@example.com", "password123");
        when(userRepository.existsByEmail("racing@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\""));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An account with this email already exists");

        verify(userProfileRepository, never()).save(any(UserProfile.class));
        verifyNoInteractions(securityEventLogger);
    }

    @Test
    void register_lowercasesEmailBeforeStoring() {
        RegisterRequest request = new RegisterRequest("MixedCase@Example.COM", "password123");
        when(userRepository.existsByEmail("mixedcase@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubTokenIssuance();

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("mixedcase@example.com");
    }

    @Test
    void register_hashesPasswordBeforeStoring_neverPersistsRawPassword() {
        RegisterRequest request = new RegisterRequest("new@example.com", "plainTextPassword123");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainTextPassword123")).thenReturn("{bcrypt}$2a$10$hashedvalue");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubTokenIssuance();

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("{bcrypt}$2a$10$hashedvalue");
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo("plainTextPassword123");
    }

    // ---- login ----

    @Test
    void login_correctCredentials_returnsAccessAndRefreshTokens() {
        User existingUser = buildUser("user@example.com", Role.USER);
        LoginRequest request = new LoginRequest("user@example.com", "correctPassword");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));
        when(jwtService.generateAccessToken(existingUser)).thenReturn("access-token-123");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-456");
        when(jwtService.hashToken("raw-refresh-456")).thenReturn("hashed-456");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token-123");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh-456");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    void login_success_auditsLoginWithTheResolvedUserIdAndNeverTheEmail() {
        User existingUser = buildUser("user@example.com", Role.USER);
        LoginRequest request = new LoginRequest("user@example.com", "correctPassword");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));
        when(jwtService.generateAccessToken(existingUser)).thenReturn("access-token-123");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-456");
        when(jwtService.hashToken("raw-refresh-456")).thenReturn("hashed-456");

        authService.login(request);

        verify(securityEventLogger).loginSucceeded(existingUser.getId());
        verify(securityEventLogger, never()).loginFailed();
    }

    @Test
    void login_wrongPassword_throwsUnauthorizedExceptionWithGenericMessage() {
        LoginRequest request = new LoginRequest("user@example.com", "wrongPassword");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_badCredentials_auditsAFailureBeforeThrowing() {
        LoginRequest request = new LoginRequest("user@example.com", "wrongPassword");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);

        verify(securityEventLogger).loginFailed();
        verify(securityEventLogger, never()).loginSucceeded(any());
    }

    @Test
    void login_nonexistentEmail_throwsExactSameMessageAsWrongPassword() {
        LoginRequest wrongPasswordRequest = new LoginRequest("existing@example.com", "wrongPassword");
        LoginRequest nonexistentEmailRequest = new LoginRequest("nobody@example.com", "anyPassword");

        when(authenticationManager.authenticate(argThat(auth -> auth != null && "existing@example.com".equals(auth.getPrincipal()))))
                .thenThrow(new BadCredentialsException("Bad credentials"));
        when(authenticationManager.authenticate(argThat(auth -> auth != null && "nobody@example.com".equals(auth.getPrincipal()))))
                .thenThrow(new BadCredentialsException("User nobody@example.com not found"));

        String wrongPasswordMessage = catchThrowableOfType(UnauthorizedException.class,
                () -> authService.login(wrongPasswordRequest)).getMessage();
        String nonexistentEmailMessage = catchThrowableOfType(UnauthorizedException.class,
                () -> authService.login(nonexistentEmailRequest)).getMessage();

        assertThat(wrongPasswordMessage).isEqualTo(nonexistentEmailMessage);
        verify(securityEventLogger, times(2)).loginFailed();
    }

    // ---- refresh ----

    @Test
    void refresh_validUnexpiredToken_rotatesAndReturnsNewTokenPair() {
        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken storedToken = buildRefreshToken(owner, "old-token-hash", LocalDateTime.now().plusDays(1), null);
        RefreshRequest request = new RefreshRequest("raw-old-token");

        when(jwtService.hashToken("raw-old-token")).thenReturn("old-token-hash");
        when(refreshTokenRepository.findByTokenHash("old-token-hash")).thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(owner)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken()).thenReturn("raw-new-token");
        when(jwtService.hashToken("raw-new-token")).thenReturn("new-token-hash");

        AuthResponse response = authService.refresh(request);

        assertThat(response.refreshToken()).isEqualTo("raw-new-token");
        assertThat(response.refreshToken()).isNotEqualTo("raw-old-token");
        assertThat(storedToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refresh_successfulRotation_auditsTokenRotationForTheOwningUser() {
        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken storedToken = buildRefreshToken(owner, "old-token-hash", LocalDateTime.now().plusDays(1), null);
        RefreshRequest request = new RefreshRequest("raw-old-token");

        when(jwtService.hashToken("raw-old-token")).thenReturn("old-token-hash");
        when(refreshTokenRepository.findByTokenHash("old-token-hash")).thenReturn(Optional.of(storedToken));
        when(jwtService.generateAccessToken(owner)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken()).thenReturn("raw-new-token");
        when(jwtService.hashToken("raw-new-token")).thenReturn("new-token-hash");

        authService.refresh(request);

        verify(securityEventLogger).refreshTokenRotated(owner.getId());
        verify(securityEventLogger, never()).refreshTokenReuseDetected(any(), anyInt());
    }

    @Test
    void refresh_revokedTokenReused_revokesEntireUserTokenSet() {
        User owner = buildUser("victim@example.com", Role.USER);
        RefreshToken reusedToken = buildRefreshToken(owner, "stolen-token-hash",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(1));
        RefreshRequest request = new RefreshRequest("raw-stolen-token");

        when(jwtService.hashToken("raw-stolen-token")).thenReturn("stolen-token-hash");
        when(refreshTokenRepository.findByTokenHash("stolen-token-hash")).thenReturn(Optional.of(reusedToken));
        when(refreshTokenRepository.revokeAllActiveByUserId(eq(owner.getId()), any(LocalDateTime.class))).thenReturn(2);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository, times(1)).revokeAllActiveByUserId(eq(owner.getId()), any(LocalDateTime.class));
    }

    @Test
    void refresh_revokedTokenReused_auditsTheftDetectionWithTheRevokedTokenCount() {
        User owner = buildUser("victim@example.com", Role.USER);
        RefreshToken reusedToken = buildRefreshToken(owner, "stolen-token-hash",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(1));
        RefreshRequest request = new RefreshRequest("raw-stolen-token");

        when(jwtService.hashToken("raw-stolen-token")).thenReturn("stolen-token-hash");
        when(refreshTokenRepository.findByTokenHash("stolen-token-hash")).thenReturn(Optional.of(reusedToken));
        when(refreshTokenRepository.revokeAllActiveByUserId(eq(owner.getId()), any(LocalDateTime.class))).thenReturn(2);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class);

        verify(securityEventLogger).refreshTokenReuseDetected(owner.getId(), 2);
        verify(securityEventLogger, never()).refreshTokenRotated(any());
    }

    @Test
    void refresh_expiredToken_throwsUnauthorizedExceptionAndAuditsNothing() {
        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken expiredToken = buildRefreshToken(owner, "expired-hash", LocalDateTime.now().minusMinutes(1), null);
        RefreshRequest request = new RefreshRequest("raw-expired-token");

        when(jwtService.hashToken("raw-expired-token")).thenReturn("expired-hash");
        when(refreshTokenRepository.findByTokenHash("expired-hash")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(jwtService, never()).generateAccessToken(any(User.class));
        verifyNoInteractions(securityEventLogger);
    }

    @Test
    void refresh_unknownTokenHash_throwsUnauthorizedException() {
        RefreshRequest request = new RefreshRequest("raw-unknown-token");
        when(jwtService.hashToken("raw-unknown-token")).thenReturn("unknown-hash");
        when(refreshTokenRepository.findByTokenHash("unknown-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        verifyNoInteractions(securityEventLogger);
    }

    // ---- logout ----

    @Test
    void logout_marksMatchingRefreshTokenRevoked() {
        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken activeToken = buildRefreshToken(owner, "active-hash", LocalDateTime.now().plusDays(1), null);
        LogoutRequest request = new LogoutRequest("raw-active-token");

        when(jwtService.hashToken("raw-active-token")).thenReturn("active-hash");
        when(refreshTokenRepository.findByTokenHash("active-hash")).thenReturn(Optional.of(activeToken));

        authService.logout(request);

        assertThat(activeToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(activeToken);
    }

    @Test
    void logout_tokenActuallyRevoked_auditsLogoutForTheOwningUser() {
        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken activeToken = buildRefreshToken(owner, "active-hash", LocalDateTime.now().plusDays(1), null);
        LogoutRequest request = new LogoutRequest("raw-active-token");

        when(jwtService.hashToken("raw-active-token")).thenReturn("active-hash");
        when(refreshTokenRepository.findByTokenHash("active-hash")).thenReturn(Optional.of(activeToken));

        authService.logout(request);

        verify(securityEventLogger).logoutSucceeded(owner.getId());
    }

    @Test
    void logout_unknownOrAlreadyRevokedToken_noOpDoesNotThrowAndAuditsNothing() {
        LogoutRequest unknownTokenRequest = new LogoutRequest("raw-unknown-token");
        when(jwtService.hashToken("raw-unknown-token")).thenReturn("unknown-hash");
        when(refreshTokenRepository.findByTokenHash("unknown-hash")).thenReturn(Optional.empty());

        assertThatCode(() -> authService.logout(unknownTokenRequest)).doesNotThrowAnyException();

        User owner = buildUser("user@example.com", Role.USER);
        RefreshToken alreadyRevoked = buildRefreshToken(owner, "already-revoked-hash",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(1));
        LogoutRequest alreadyRevokedRequest = new LogoutRequest("raw-already-revoked-token");
        when(jwtService.hashToken("raw-already-revoked-token")).thenReturn("already-revoked-hash");
        when(refreshTokenRepository.findByTokenHash("already-revoked-hash")).thenReturn(Optional.of(alreadyRevoked));

        assertThatCode(() -> authService.logout(alreadyRevokedRequest)).doesNotThrowAnyException();

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verifyNoInteractions(securityEventLogger);
    }

    // ---- changePassword ----

    @Test
    void changePassword_attemptBudgetExhausted_throwsRateLimitExceededWithoutCheckingThePassword() {
        UUID userId = UUID.randomUUID();
        when(rateLimiter.tryConsume(userId.toString(), "password-change", 5, Duration.ofMinutes(15))).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(userId,
                new ChangePasswordRequest("old-password", "brand-new-password")))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(passwordEncoder, userRepository, refreshTokenRepository, securityEventLogger);
    }

    @Test
    void changePassword_consumesTheRateLimitBeforeCheckingTheCurrentPassword() {
        User user = buildUser("user@example.com", Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "existing-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(user.getId(),
                new ChangePasswordRequest("wrong-password", "brand-new-password")))
                .isInstanceOf(BadRequestException.class);

        InOrder order = inOrder(rateLimiter, passwordEncoder);
        order.verify(rateLimiter).tryConsume(user.getId().toString(), "password-change", 5, Duration.ofMinutes(15));
        order.verify(passwordEncoder).matches("wrong-password", "existing-hash");
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsBadRequestAuditsARejectionAndChangesNothing() {
        User user = buildUser("user@example.com", Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "existing-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(user.getId(),
                new ChangePasswordRequest("wrong-password", "brand-new-password")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The current password is incorrect");

        verify(securityEventLogger).passwordChangeRejected(user.getId());
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
        verify(jwtService, never()).generateAccessToken(any());
        assertThat(user.getPasswordHash()).isEqualTo("existing-hash");
    }

    @Test
    void changePassword_newPasswordEqualToCurrent_throwsBadRequestAndChangesNothing() {
        User user = buildUser("user@example.com", Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("same-password", "existing-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(user.getId(),
                new ChangePasswordRequest("same-password", "same-password")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The new password must differ from the current one");

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
        verify(securityEventLogger, never()).passwordChanged(any(), anyInt());
    }

    @Test
    void changePassword_success_storesTheNewHashRevokesEveryActiveTokenAndIssuesAFreshPair() {
        User user = buildUser("user@example.com", Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "existing-hash")).thenReturn(true);
        when(passwordEncoder.encode("brand-new-password")).thenReturn("new-hash");
        when(refreshTokenRepository.revokeAllActiveByUserId(eq(user.getId()), any(LocalDateTime.class))).thenReturn(2);
        stubTokenIssuance();

        AuthResponse response = authService.changePassword(user.getId(),
                new ChangePasswordRequest("old-password", "brand-new-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        InOrder order = inOrder(userRepository, refreshTokenRepository);
        order.verify(userRepository).save(user);
        order.verify(refreshTokenRepository).revokeAllActiveByUserId(eq(user.getId()), any(LocalDateTime.class));
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-refresh-token");
    }

    @Test
    void changePassword_success_auditsAPasswordChangeWithTheRevokedCountNeverATheftDetection() {
        User user = buildUser("user@example.com", Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "existing-hash")).thenReturn(true);
        when(passwordEncoder.encode("brand-new-password")).thenReturn("new-hash");
        when(refreshTokenRepository.revokeAllActiveByUserId(eq(user.getId()), any(LocalDateTime.class))).thenReturn(2);
        stubTokenIssuance();

        authService.changePassword(user.getId(), new ChangePasswordRequest("old-password", "brand-new-password"));

        verify(securityEventLogger).passwordChanged(user.getId(), 2);
        verify(securityEventLogger, never()).refreshTokenReuseDetected(any(), anyInt());
        verify(securityEventLogger, never()).passwordChangeRejected(any());
    }

    @Test
    void changePassword_noActiveTokens_stillSucceedsAndAuditsZeroRevoked() {
        User user = buildUser("user@example.com", Role.USER);
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "existing-hash")).thenReturn(true);
        when(passwordEncoder.encode("brand-new-password")).thenReturn("new-hash");
        when(refreshTokenRepository.revokeAllActiveByUserId(eq(user.getId()), any(LocalDateTime.class))).thenReturn(0);
        stubTokenIssuance();

        AuthResponse response = authService.changePassword(user.getId(),
                new ChangePasswordRequest("old-password", "brand-new-password"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(securityEventLogger).passwordChanged(eq(user.getId()), eq(0));
    }

    @Test
    void changePassword_userNoLongerExists_throwsIllegalState() {
        UUID userId = UUID.randomUUID();
        when(rateLimiter.tryConsume(any(), any(), anyInt(), any())).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(userId,
                new ChangePasswordRequest("old-password", "brand-new-password")))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(passwordEncoder);
    }
}
