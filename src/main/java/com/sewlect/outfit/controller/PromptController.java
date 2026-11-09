package com.sewlect.outfit.controller;

import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.dto.PromptGenerationRequest;
import com.sewlect.outfit.dto.PromptRefinementRequest;
import com.sewlect.outfit.service.PromptGenerationQuotaService;
import com.sewlect.outfit.service.PromptOutfitGenerationService;
import com.sewlect.outfit.service.PromptRefinementService;
import com.sewlect.outfit.support.SlotEditTargetResolver;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outfits")
@AllArgsConstructor
@Tag(name = "AI prompt", description = "Free-text prompt to a batch of outfits, and prompt-driven refinement of one garment slot")
public class PromptController {

    private final PromptOutfitGenerationService promptOutfitGenerationService;
    private final PromptRefinementService promptRefinementService;
    private final PromptGenerationQuotaService promptGenerationQuotaService;
    private final SlotEditTargetResolver slotEditTargetResolver;

    @Operation(summary = "Generate a batch of outfits from a free-text prompt; matchProfile=false makes the AI infer gender and budget from the prompt instead of the caller's profile")
    @StandardApiErrors
    @ApiResponse(responseCode = "429",
            description = "The caller's hourly prompt-generation budget is exhausted",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/prompt")
    public ResponseEntity<List<OutfitResponse>> generate(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody PromptGenerationRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        promptGenerationQuotaService.consumeGeneration(userId, 1);
        boolean matchProfile = request.matchProfile() == null || request.matchProfile();
        return ResponseEntity.ok(promptOutfitGenerationService.generate(userId, request.prompt(), matchProfile));
    }

    @Operation(summary = "Browse prompt-driven alternatives for one garment slot; read-only, commit the choice through the swap endpoint")
    @StandardApiErrors
    @ApiResponse(responseCode = "429",
            description = "The caller's hourly prompt-refinement budget is exhausted",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{outfitId}/items/{itemId}/refine")
    public ResponseEntity<List<AlternativeCandidateResponse>> refine(@AuthenticationPrincipal Jwt jwt,
                                                                     @PathVariable UUID outfitId,
                                                                     @PathVariable UUID itemId,
                                                                     @Valid @RequestBody PromptRefinementRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        promptGenerationQuotaService.consumeRefinement(userId);
        return ResponseEntity.ok(promptRefinementService.refine(
                slotEditTargetResolver.forItem(outfitId, itemId), userId, request.prompt()));
    }

    @Operation(summary = "Browse prompt-driven candidates for an empty optional slot; read-only, commit the choice through the add endpoint",
            description = "role is uppercase and must be OUTERWEAR or ACCESSORY; any other role returns 400, and a slot the outfit already fills returns 409")
    @StandardApiErrors
    @ApiResponse(responseCode = "429",
            description = "The caller's hourly prompt-refinement budget is exhausted",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{outfitId}/slots/{role}/refine")
    public ResponseEntity<List<AlternativeCandidateResponse>> refineEmptySlot(@AuthenticationPrincipal Jwt jwt,
                                                                              @PathVariable UUID outfitId,
                                                                              @PathVariable GarmentRole role,
                                                                              @Valid @RequestBody PromptRefinementRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        promptGenerationQuotaService.consumeRefinement(userId);
        return ResponseEntity.ok(promptRefinementService.refine(
                slotEditTargetResolver.forEmptySlot(outfitId, role), userId, request.prompt()));
    }
}