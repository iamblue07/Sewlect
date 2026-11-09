package com.sewlect.stylist.controller;

import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.stylist.annotation.StylistTurnApiErrors;
import com.sewlect.stylist.dto.ConfirmProposalRequest;
import com.sewlect.stylist.dto.ConversationPageResponse;
import com.sewlect.stylist.dto.ConversationResponse;
import com.sewlect.stylist.dto.ConversationResultsResponse;
import com.sewlect.stylist.dto.ConversationSummaryResponse;
import com.sewlect.stylist.dto.SendMessageRequest;
import com.sewlect.stylist.dto.StartConversationRequest;
import com.sewlect.stylist.dto.TurnResponse;
import com.sewlect.stylist.service.StylistConversationQueryService;
import com.sewlect.stylist.service.StylistConversationService;
import com.sewlect.stylist.service.StylistExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stylist/conversations")
@AllArgsConstructor
@Tag(name = "Stylist", description = "Chat with a fashion stylist that proposes outfit searches and runs them on confirmation")
public class StylistConversationController {

    private final StylistConversationService stylistConversationService;
    private final StylistExecutionService stylistExecutionService;
    private final StylistConversationQueryService stylistConversationQueryService;

    @Operation(summary = "Start a chat with the stylist; the reply follows the user's language")
    @StandardApiErrors
    @StylistTurnApiErrors
    @ApiResponse(responseCode = "502", description = "The stylist model call failed; nothing was stored, resend the message",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "The message was blocked by the content safety check and counts as a strike",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<TurnResponse> start(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody StartConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stylistConversationService.start(userId(jwt), request.message()));
    }

    @Operation(summary = "List the caller's chats, most recently active first; size is clamped to the configured maximum")
    @StandardApiErrors
    @GetMapping
    public ResponseEntity<ConversationPageResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(stylistConversationQueryService.list(userId(jwt), page, size));
    }

    @Operation(summary = "Get one of the caller's chats with its full message history")
    @StandardApiErrors
    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> get(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID conversationId) {
        return ResponseEntity.ok(stylistConversationQueryService.get(userId(jwt), conversationId));
    }

    @Operation(summary = "Send a message to an open chat; 409 once the chat is closed or its turn limit is reached")
    @StandardApiErrors
    @StylistTurnApiErrors
    @ApiResponse(responseCode = "502", description = "The stylist model call failed; nothing was stored, resend the message",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "The message was blocked by the content safety check and counts as a strike",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<TurnResponse> sendMessage(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID conversationId,
                                                    @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(stylistConversationService.sendMessage(userId(jwt), conversationId, request.content()));
    }

    @Operation(summary = "Ask the stylist for the confirmation summary now; available regardless of the turn limit")
    @StandardApiErrors
    @StylistTurnApiErrors
    @ApiResponse(responseCode = "502", description = "The stylist model call failed; nothing was stored, try again",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{conversationId}/proposal")
    public ResponseEntity<TurnResponse> requestProposal(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID conversationId) {
        return ResponseEntity.ok(stylistConversationService.requestProposal(userId(jwt), conversationId));
    }

    @Operation(summary = "Confirm the latest summary; its outfit search runs in the background and the chat closes",
            description = "409 when the message is not the chat's latest summary or the chat is not open. The search "
                    + "consumes one unit of the shared hourly prompt-generation budget.")
    @StandardApiErrors
    @ApiResponse(responseCode = "202", description = "The searches were queued; poll the chat or its results")
    @ApiResponse(responseCode = "429", description = "The caller's hourly prompt-generation budget is exhausted",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "The search queue is full; the chat stays open, confirm again later",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{conversationId}/confirm")
    public ResponseEntity<ConversationSummaryResponse> confirm(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID conversationId,
                                                               @Valid @RequestBody ConfirmProposalRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(stylistExecutionService.confirm(userId(jwt), conversationId, request.messageId()));
    }

    @Operation(summary = "Get the confirmed searches of a chat in position order, each with its status and outfits")
    @StandardApiErrors
    @GetMapping("/{conversationId}/results")
    public ResponseEntity<ConversationResultsResponse> results(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID conversationId) {
        return ResponseEntity.ok(stylistConversationQueryService.results(userId(jwt), conversationId));
    }

    @Operation(summary = "Delete one of the caller's chats; 409 while its searches are running")
    @StandardApiErrors
    @ApiResponse(responseCode = "204", description = "The chat, its messages and its searches were deleted")
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID conversationId) {
        stylistConversationService.delete(userId(jwt), conversationId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
