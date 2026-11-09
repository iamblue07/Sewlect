package com.sewlect.social.service;

import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.dto.OutfitResponse;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitItemQueryService;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import com.sewlect.social.dto.InteractionStateResponse;
import com.sewlect.social.dto.SavedOutfitsResponse;
import com.sewlect.social.entity.UserOutfitInteraction;
import com.sewlect.social.enums.InteractionType;
import com.sewlect.social.repository.UserOutfitInteractionRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
public class OutfitInteractionService {

    private static final Logger log = LoggerFactory.getLogger(OutfitInteractionService.class);

    private final UserOutfitInteractionRepository userOutfitInteractionRepository;
    private final OutfitItemQueryService outfitItemQueryService;
    private final OutfitResponseAssembler outfitResponseAssembler;
    private final UserReferenceQueryService userReferenceQueryService;
    private final OutfitEngagementService outfitEngagementService;

    public InteractionStateResponse activate(UUID userId, UUID outfitId, InteractionType type) {
        if (userOutfitInteractionRepository.existsByUserIdAndOutfitIdAndInteractionType(userId, outfitId, type)) {
            return new InteractionStateResponse(true, outfitEngagementService.readOne(outfitId, userId));
        }

        Outfit outfit = outfitItemQueryService.getById(outfitId);

        UserOutfitInteraction interaction = UserOutfitInteraction.builder()
                .user(userReferenceQueryService.getReference(userId))
                .outfit(outfit)
                .interactionType(type)
                .build();

        try {
            userOutfitInteractionRepository.saveAndFlush(interaction);
        } catch (DataIntegrityViolationException e) {
            log.debug("Interaction {} already active for user {} and outfit {}", type, userId, outfitId);
        }

        return new InteractionStateResponse(true, outfitEngagementService.readOne(outfitId, userId));
    }

    @Transactional
    public InteractionStateResponse deactivate(UUID userId, UUID outfitId, InteractionType type) {
        userOutfitInteractionRepository.deleteByUserIdAndOutfitIdAndInteractionType(userId, outfitId, type);
        return new InteractionStateResponse(false, outfitEngagementService.readOne(outfitId, userId));
    }

    @Transactional(readOnly = true)
    public SavedOutfitsResponse listSaved(UUID userId, Pageable pageable) {
        Page<UserOutfitInteraction> page = userOutfitInteractionRepository
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(userId, InteractionType.SAVE, pageable);

        List<Outfit> outfits = page.getContent().stream()
                .map(UserOutfitInteraction::getOutfit)
                .toList();

        List<OutfitResponse> items = outfitResponseAssembler.toResponses(outfits, userId);

        return new SavedOutfitsResponse(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Set<UUID> listInteractedOutfitIds(UUID userId, InteractionType type) {
        return new LinkedHashSet<>(userOutfitInteractionRepository
                .findOutfitIdsByUserIdAndInteractionType(userId, type));
    }
}