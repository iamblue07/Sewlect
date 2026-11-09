package com.sewlect.social.service;

import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.outfit.service.OutfitEngagementReader;
import com.sewlect.social.enums.InteractionType;
import com.sewlect.social.repository.UserOutfitInteractionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class OutfitEngagementService implements OutfitEngagementReader {

    private final UserOutfitInteractionRepository userOutfitInteractionRepository;

    @Override
    public Map<UUID, OutfitEngagement> read(Collection<UUID> outfitIds, UUID viewerId) {
        if (outfitIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> likeCounts = readLikeCounts(outfitIds);

        Set<UUID> likedByViewer = new HashSet<>();
        Set<UUID> savedByViewer = new HashSet<>();
        for (UserOutfitInteractionRepository.ViewerInteraction interaction
                : userOutfitInteractionRepository.findViewerInteractions(viewerId, outfitIds)) {
            if (interaction.getInteractionType() == InteractionType.LIKE) {
                likedByViewer.add(interaction.getOutfitId());
            } else if (interaction.getInteractionType() == InteractionType.SAVE) {
                savedByViewer.add(interaction.getOutfitId());
            }
        }

        Map<UUID, OutfitEngagement> engagementByOutfitId = new HashMap<>();
        for (UUID outfitId : outfitIds) {
            engagementByOutfitId.put(outfitId, new OutfitEngagement(
                    likeCounts.getOrDefault(outfitId, 0L),
                    likedByViewer.contains(outfitId),
                    savedByViewer.contains(outfitId)));
        }
        return engagementByOutfitId;
    }

    @Override
    public Map<UUID, Long> readLikeCounts(Collection<UUID> outfitIds) {
        if (outfitIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> likeCounts = new HashMap<>();
        for (UserOutfitInteractionRepository.OutfitInteractionCount count
                : userOutfitInteractionRepository.countByOutfitIdsAndInteractionType(outfitIds, InteractionType.LIKE)) {
            likeCounts.put(count.getOutfitId(), count.getInteractionCount());
        }
        return likeCounts;
    }

    public OutfitEngagement readOne(UUID outfitId, UUID viewerId) {
        return read(Set.of(outfitId), viewerId).getOrDefault(outfitId, OutfitEngagement.none());
    }
}
