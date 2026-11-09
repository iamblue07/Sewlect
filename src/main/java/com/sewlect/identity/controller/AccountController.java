package com.sewlect.identity.controller;

import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.identity.dto.AuthResponse;
import com.sewlect.identity.dto.ChangePasswordRequest;
import com.sewlect.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@AllArgsConstructor
@Tag(name = "Account", description = "Credential management for the caller's own account")
public class AccountController {

    private final AuthService authService;

    @Operation(summary = "Change the caller's password; revokes every refresh token and returns a fresh token pair for this device",
            description = "A wrong current password, or a new password equal to the current one, returns 400 rather than 401. "
                    + "Access tokens already issued to other devices stay valid until they expire.")
    @StandardApiErrors
    @ApiResponse(responseCode = "429",
            description = "The caller's password-change attempt budget is exhausted",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(authService.changePassword(userId, request));
    }
}
