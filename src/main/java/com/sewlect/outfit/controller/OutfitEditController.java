package com.sewlect.outfit.controller;

import com.sewlect.common.openapi.annotation.StandardApiErrors;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.outfit.dto.AlternativeCandidateResponse;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.dto.ProductSelectionRequest;
import com.sewlect.outfit.service.GarmentAlternativesService;
import com.sewlect.outfit.service.OutfitEditService;
import com.sewlect.outfit.support.SlotEditTargetResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outfits/{outfitId}")
@AllArgsConstructor
@Tag(name = "Outfit editing", description = "Browsing alternatives for one garment slot, and swapping, removing or adding a garment; every edit creates a new outfit rather than mutating the existing one")
public class OutfitEditController {

    private final SlotEditTargetResolver slotEditTargetResolver;
    private final GarmentAlternativesService garmentAlternativesService;
    private final OutfitEditService outfitEditService;

    @Operation(summary = "List the most outfit-compatible alternatives of the same article type for one garment, ranked by projected score; nothing is persisted")
    @StandardApiErrors
    @GetMapping("/items/{itemId}/alternatives")
    public ResponseEntity<List<AlternativeCandidateResponse>> listItemAlternatives(@AuthenticationPrincipal Jwt jwt,
                                                                                   @PathVariable UUID outfitId,
                                                                                   @PathVariable UUID itemId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(garmentAlternativesService.listAlternatives(
                slotEditTargetResolver.forItem(outfitId, itemId), userId));
    }

    @Operation(summary = "Swap one garment for any product of the same garment role, creating a new outfit")
    @StandardApiErrors
    @PostMapping("/items/{itemId}/swap")
    public ResponseEntity<OutfitResponse> swap(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID outfitId,
                                               @PathVariable UUID itemId,
                                               @Valid @RequestBody ProductSelectionRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitEditService.swap(
                slotEditTargetResolver.forItem(outfitId, itemId), request.productId(), userId));
    }

    @Operation(summary = "Remove an OUTERWEAR or ACCESSORY item, creating a new outfit; removing any other role returns 400")
    @StandardApiErrors
    @PostMapping("/items/{itemId}/remove")
    public ResponseEntity<OutfitResponse> remove(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID outfitId,
                                                 @PathVariable UUID itemId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitEditService.remove(slotEditTargetResolver.forItem(outfitId, itemId), userId));
    }

    @Operation(summary = "List the most outfit-compatible products for an empty optional slot, ranked by projected score; nothing is persisted",
            description = "role is uppercase and must be OUTERWEAR or ACCESSORY; any other role returns 400, and a slot the outfit already fills returns 409")
    @StandardApiErrors
    @GetMapping("/slots/{role}/alternatives")
    public ResponseEntity<List<AlternativeCandidateResponse>> listSlotAlternatives(@AuthenticationPrincipal Jwt jwt,
                                                                                   @PathVariable UUID outfitId,
                                                                                   @PathVariable GarmentRole role) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(garmentAlternativesService.listAlternatives(
                slotEditTargetResolver.forEmptySlot(outfitId, role), userId));
    }

    @Operation(summary = "Add a garment to an empty optional slot, creating a new outfit",
            description = "role is uppercase and must be OUTERWEAR or ACCESSORY; any other role returns 400, and a slot the outfit already fills returns 409 - swap that item instead")
    @StandardApiErrors
    @PostMapping("/slots/{role}/add")
    public ResponseEntity<OutfitResponse> add(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID outfitId,
                                              @PathVariable GarmentRole role,
                                              @Valid @RequestBody ProductSelectionRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(outfitEditService.add(
                slotEditTargetResolver.forEmptySlot(outfitId, role), request.productId(), userId));
    }
}
