package com.sewlect.outfit.controller;

import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitItemQueryService;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outfits")
@AllArgsConstructor
@Tag(name = "Outfits", description = "Direct lookup of a single outfit, as a share link resolves it")
public class OutfitController {

    private final OutfitItemQueryService outfitItemQueryService;
    private final OutfitResponseAssembler outfitResponseAssembler;

    @Operation(summary = "Get one outfit by id; outfits are shared, profile-independent rows so any authenticated caller can resolve one")
    @StandardApiErrors
    @GetMapping("/{outfitId}")
    public ResponseEntity<OutfitResponse> getOutfit(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID outfitId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        Outfit outfit = outfitItemQueryService.getById(outfitId);
        return ResponseEntity.ok(outfitResponseAssembler.toResponse(outfit, userId));
    }
}