package com.sewlect.outfit.controller;

import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.outfit.dto.PublicOutfitResponse;
import com.sewlect.outfit.service.PublicOutfitQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/outfits")
@AllArgsConstructor
@Tag(name = "Public outfits", description = "Unauthenticated, rate-limited view of a shared outfit")
public class PublicOutfitController {

    private final PublicOutfitQueryService publicOutfitQueryService;

    @Operation(summary = "View a shared outfit without an account; carries no scores, item ids or viewer state")
    @SecurityRequirements
    @ApiResponse(responseCode = "404",
            description = "No outfit exists with this id",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429",
            description = "The caller's IP has exhausted its public-API request budget",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{outfitId}")
    public ResponseEntity<PublicOutfitResponse> getOutfit(@PathVariable UUID outfitId) {
        return ResponseEntity.ok(publicOutfitQueryService.getPublicView(outfitId));
    }
}
