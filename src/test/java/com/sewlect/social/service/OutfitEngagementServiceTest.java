package com.sewlect.social.service;

import com.sewlect.outfit.domain.OutfitEngagement;
import com.sewlect.social.enums.InteractionType;
import com.sewlect.social.repository.UserOutfitInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitEngagementServiceTest {

    @Mock
    private UserOutfitInteractionRepository userOutfitInteractionRepository;

    private OutfitEngagementService service;

    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OutfitEngagementService(userOutfitInteractionRepository);
    }

    @Test
    void read_emptyIdSet_returnsEmptyMapWithoutQuerying() {
        assertThat(service.read(List.of(), viewerId)).isEmpty();
        verifyNoInteractions(userOutfitInteractionRepository);
    }

    @Test
    void readLikeCounts_emptyIdSet_returnsEmptyMapWithoutQuerying() {
        assertThat(service.readLikeCounts(List.of())).isEmpty();
        verifyNoInteractions(userOutfitInteractionRepository);
    }

    @Test
    void readLikeCounts_mapsEachCountedOutfitAndCountsOnlyLikes() {
        UUID liked = UUID.randomUUID();
        UUID unliked = UUID.randomUUID();
        List<UUID> ids = List.of(liked, unliked);
        when(userOutfitInteractionRepository.countByOutfitIdsAndInteractionType(ids, InteractionType.LIKE))
                .thenReturn(List.of(count(liked, 4L)));

        Map<UUID, Long> counts = service.readLikeCounts(ids);

        assertThat(counts).containsExactly(Map.entry(liked, 4L));
        verify(userOutfitInteractionRepository).countByOutfitIdsAndInteractionType(ids, InteractionType.LIKE);
    }

    @Test
    void read_combinesLikeCountsWithTheViewersOwnLikesAndSaves() {
        UUID likedAndSaved = UUID.randomUUID();
        UUID savedOnly = UUID.randomUUID();
        UUID untouched = UUID.randomUUID();
        List<UUID> ids = List.of(likedAndSaved, savedOnly, untouched);
        when(userOutfitInteractionRepository.countByOutfitIdsAndInteractionType(ids, InteractionType.LIKE))
                .thenReturn(List.of(count(likedAndSaved, 3L), count(savedOnly, 1L)));
        when(userOutfitInteractionRepository.findViewerInteractions(viewerId, ids)).thenReturn(List.of(
                viewer(likedAndSaved, InteractionType.LIKE),
                viewer(likedAndSaved, InteractionType.SAVE),
                viewer(savedOnly, InteractionType.SAVE)));

        Map<UUID, OutfitEngagement> engagement = service.read(ids, viewerId);

        assertThat(engagement.get(likedAndSaved)).isEqualTo(new OutfitEngagement(3L, true, true));
        assertThat(engagement.get(savedOnly)).isEqualTo(new OutfitEngagement(1L, false, true));
        assertThat(engagement.get(untouched)).isEqualTo(OutfitEngagement.none());
    }

    @Test
    void read_otherUsersLikesCountButNeverMarkTheViewerAsLiking() {
        UUID outfitId = UUID.randomUUID();
        List<UUID> ids = List.of(outfitId);
        when(userOutfitInteractionRepository.countByOutfitIdsAndInteractionType(ids, InteractionType.LIKE))
                .thenReturn(List.of(count(outfitId, 2L)));
        when(userOutfitInteractionRepository.findViewerInteractions(viewerId, ids)).thenReturn(List.of());

        OutfitEngagement engagement = service.read(ids, viewerId).get(outfitId);

        assertThat(engagement.likeCount()).isEqualTo(2L);
        assertThat(engagement.likedByViewer()).isFalse();
        assertThat(engagement.savedByViewer()).isFalse();
    }

    @Test
    void read_shareInteractionsNeverSetLikedOrSaved() {
        UUID outfitId = UUID.randomUUID();
        List<UUID> ids = List.of(outfitId);
        when(userOutfitInteractionRepository.countByOutfitIdsAndInteractionType(ids, InteractionType.LIKE))
                .thenReturn(List.of());
        when(userOutfitInteractionRepository.findViewerInteractions(viewerId, ids))
                .thenReturn(List.of(viewer(outfitId, InteractionType.SHARE)));

        assertThat(service.read(ids, viewerId).get(outfitId)).isEqualTo(OutfitEngagement.none());
    }

    @Test
    void readOne_returnsTheEngagementForThatSingleOutfit() {
        UUID outfitId = UUID.randomUUID();
        when(userOutfitInteractionRepository.countByOutfitIdsAndInteractionType(
                Set.of(outfitId), InteractionType.LIKE)).thenReturn(List.of(count(outfitId, 5L)));
        when(userOutfitInteractionRepository.findViewerInteractions(viewerId, Set.of(outfitId)))
                .thenReturn(List.of(viewer(outfitId, InteractionType.LIKE)));

        assertThat(service.readOne(outfitId, viewerId)).isEqualTo(new OutfitEngagement(5L, true, false));
    }

    private UserOutfitInteractionRepository.OutfitInteractionCount count(UUID outfitId, long interactionCount) {
        return new UserOutfitInteractionRepository.OutfitInteractionCount() {
            @Override
            public UUID getOutfitId() {
                return outfitId;
            }

            @Override
            public long getInteractionCount() {
                return interactionCount;
            }
        };
    }

    private UserOutfitInteractionRepository.ViewerInteraction viewer(UUID outfitId, InteractionType type) {
        return new UserOutfitInteractionRepository.ViewerInteraction() {
            @Override
            public UUID getOutfitId() {
                return outfitId;
            }

            @Override
            public InteractionType getInteractionType() {
                return type;
            }
        };
    }
}
