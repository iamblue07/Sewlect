package com.sewlect.social.controller;

import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.common.persistence.util.PageRequests;
import com.sewlect.social.dto.InteractionStateResponse;
import com.sewlect.social.dto.SavedOutfitsResponse;
import com.sewlect.social.enums.InteractionType;
import com.sewlect.social.properties.SocialProperties;
import com.sewlect.social.service.OutfitInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outfits")
@AllArgsConstructor
@EnableConfigurationProperties(SocialProperties.class)
@Tag(name = "Social interactions", description = "Likes and saves on outfits, and the caller's saved list")
public class OutfitInteractionController {

    private final OutfitInteractionService outfitInteractionService;
    private final SocialProperties socialProperties;

    @Operation(summary = "Like an outfit; idempotent, liking an already-liked outfit is a no-op")
    @StandardApiErrors
    @PostMapping("/{outfitId}/like")
    public ResponseEntity<InteractionStateResponse> like(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID outfitId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.activate(userId, outfitId, InteractionType.LIKE));
    }

    @Operation(summary = "Remove a like; idempotent, unliking a not-liked outfit is a no-op")
    @StandardApiErrors
    @DeleteMapping("/{outfitId}/like")
    public ResponseEntity<InteractionStateResponse> unlike(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID outfitId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.deactivate(userId, outfitId, InteractionType.LIKE));
    }

    @Operation(summary = "Save an outfit; idempotent, saving an already-saved outfit is a no-op")
    @StandardApiErrors
    @PostMapping("/{outfitId}/save")
    public ResponseEntity<InteractionStateResponse> save(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID outfitId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.activate(userId, outfitId, InteractionType.SAVE));
    }

    @Operation(summary = "Remove a save; idempotent, unsaving a not-saved outfit is a no-op")
    @StandardApiErrors
    @DeleteMapping("/{outfitId}/save")
    public ResponseEntity<InteractionStateResponse> unsave(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID outfitId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.deactivate(userId, outfitId, InteractionType.SAVE));
    }

    @Operation(summary = "List the caller's saved outfits, most recently saved first; size is clamped to the configured maximum")
    @StandardApiErrors
    @GetMapping("/saved")
    public ResponseEntity<SavedOutfitsResponse> listSaved(@AuthenticationPrincipal Jwt jwt,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Pageable pageable = PageRequests.of(page, clampSize(size));
        return ResponseEntity.ok(outfitInteractionService.listSaved(userId, pageable));
    }

    @Operation(summary = "Get every outfit id the caller has liked or saved, so a client can reconcile heart and bookmark state locally. "
            + "Values are uppercase: LIKE, SAVE or SHARE. SHARE is accepted but nothing writes share rows yet, so it always returns an empty set")
    @StandardApiErrors
    @GetMapping("/interactions/mine")
    public ResponseEntity<Set<UUID>> listInteractedOutfitIds(@AuthenticationPrincipal Jwt jwt,
                                                             @RequestParam InteractionType type) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitInteractionService.listInteractedOutfitIds(userId, type));
    }

    private int clampSize(int size) {
        if (size < 1) {
            return socialProperties.defaultPageSize();
        }
        return Math.min(size, socialProperties.maxPageSize());
    }
}