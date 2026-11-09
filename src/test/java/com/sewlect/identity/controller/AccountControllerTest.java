package com.sewlect.identity.controller;

import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.RateLimitExceededException;
import com.sewlect.identity.dto.AuthResponse;
import com.sewlect.identity.dto.ChangePasswordRequest;
import com.sewlect.identity.service.AuthService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(WebSliceTestConfig.class)
@TestPropertySource(properties = {
        WebSliceTestConfig.JWT_SECRET_PROPERTY,
        WebSliceTestConfig.JWT_ACCESS_EXPIRATION_PROPERTY,
        WebSliceTestConfig.JWT_REFRESH_EXPIRATION_PROPERTY,
        WebSliceTestConfig.SUBJECT_HASH_SECRET_PROPERTY
})
class AccountControllerTest {

    private static final String VALID_BODY = """
            {"currentPassword": "old-password", "newPassword": "brand-new-password"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void changePassword_missingAuthorizationHeader_returns401AndNeverInvokesService() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(authService);
    }

    @Test
    void changePassword_validRequest_returns200WithTheNewTokenPairForTheJwtSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.changePassword(userId, new ChangePasswordRequest("old-password", "brand-new-password")))
                .thenReturn(AuthResponse.of("new-access-token", "new-refresh-token", 900L));

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).changePassword(eq(userId), any());
    }

    @Test
    void changePassword_blankCurrentPassword_returns400WithFieldErrorAndNeverInvokesService() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"\", \"newPassword\": \"brand-new-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void changePassword_newPasswordShorterThanEight_returns400WithFieldErrorAndNeverInvokesService() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"old-password\", \"newPassword\": \"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void changePassword_wrongCurrentPassword_returns400Not401() throws Exception {
        when(authService.changePassword(any(), any()))
                .thenThrow(new BadRequestException("The current password is incorrect"));

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("The current password is incorrect"));
    }

    @Test
    void changePassword_attemptBudgetExhausted_returns429() throws Exception {
        when(authService.changePassword(any(), any()))
                .thenThrow(new RateLimitExceededException("Too many password change attempts - try again later"));

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WebSliceTestConfig.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }
}
