package com.sewlect.social.service;

import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.taxonomy.enums.GarmentRole;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.service.UserReferenceQueryService;
import com.sewlect.outfit.domain.OutfitItemView;
import com.sewlect.outfit.entity.Outfit;
import com.sewlect.outfit.service.OutfitItemQueryService;
import com.sewlect.outfit.support.OutfitResponseAssembler;
import com.sewlect.outfit.support.OutfitViewAssembler;
import com.sewlect.social.dto.InteractionStateResponse;
import com.sewlect.social.dto.SavedOutfitsResponse;
import com.sewlect.social.entity.UserOutfitInteraction;
import com.sewlect.social.enums.InteractionType;
import com.sewlect.social.repository.UserOutfitInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.sewlect.outfit.support.OutfitViewCache;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitInteractionServiceTest {

    @Mock
    private UserOutfitInteractionRepository userOutfitInteractionRepository;

    @Mock
    private OutfitItemQueryService outfitItemQueryService;

    @Mock
    private OutfitViewCache outfitViewCache;

    @Mock
    private UserReferenceQueryService userReferenceQueryService;

    private OutfitResponseAssembler outfitResponseAssembler;

    private OutfitInteractionService service;

    @BeforeEach
    void setUp() {
        OutfitEngagementService outfitEngagementService = new OutfitEngagementService(userOutfitInteractionRepository);
        outfitResponseAssembler = new OutfitResponseAssembler(
                new OutfitViewAssembler(outfitItemQueryService, outfitViewCache), outfitEngagementService);
        service = new OutfitInteractionService(
                userOutfitInteractionRepository, outfitItemQueryService, outfitResponseAssembler,
                userReferenceQueryService, outfitEngagementService);
    }

    // ---------- activate ----------

    @Test
    void activate_notYetActive_createsRowAndReturnsActiveTrue() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        User user = userWith(userId);
        Outfit outfit = outfitWith(outfitId);

        when(userOutfitInteractionRepository
                .existsByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.LIKE))
                .thenReturn(false);
        when(outfitItemQueryService.getById(outfitId)).thenReturn(outfit);
        when(userReferenceQueryService.getReference(userId)).thenReturn(user);

        InteractionStateResponse response = service.activate(userId, outfitId, InteractionType.LIKE);

        assertThat(response.active()).isTrue();

        ArgumentCaptor<UserOutfitInteraction> captor = ArgumentCaptor.forClass(UserOutfitInteraction.class);
        verify(userOutfitInteractionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getOutfit()).isSameAs(outfit);
        assertThat(captor.getValue().getInteractionType()).isEqualTo(InteractionType.LIKE);
    }

    @Test
    void activate_alreadyActive_shortCircuitsOnExistsCheckAndNeverCallsSave() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();

        when(userOutfitInteractionRepository
                .existsByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.SAVE))
                .thenReturn(true);

        InteractionStateResponse response = service.activate(userId, outfitId, InteractionType.SAVE);

        assertThat(response.active()).isTrue();
        verify(userOutfitInteractionRepository, never()).saveAndFlush(any());
        verify(outfitItemQueryService, never()).getById(any());
    }

    @Test
    void activate_concurrentInsertRace_catchesConstraintViolationAndStillReturnsActiveTrue() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();

        when(userOutfitInteractionRepository
                .existsByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.LIKE))
                .thenReturn(false);
        when(outfitItemQueryService.getById(outfitId)).thenReturn(outfitWith(outfitId));
        when(userReferenceQueryService.getReference(userId)).thenReturn(userWith(userId));
        when(userOutfitInteractionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates uq_user_outfit_interactions"));

        InteractionStateResponse response = service.activate(userId, outfitId, InteractionType.LIKE);

        assertThat(response.active()).isTrue();
        verify(userOutfitInteractionRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void activate_outfitDoesNotExist_throwsResourceNotFoundExceptionBeforeAttemptingInsert() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();

        when(userOutfitInteractionRepository
                .existsByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.SAVE))
                .thenReturn(false);
        when(outfitItemQueryService.getById(outfitId))
                .thenThrow(new ResourceNotFoundException("Outfit not found: " + outfitId));

        assertThatThrownBy(() -> service.activate(userId, outfitId, InteractionType.SAVE))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userOutfitInteractionRepository, never()).saveAndFlush(any());
    }

    @Test
    void activate_likeAndSaveOnSameOutfit_bothPersistIndependently() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        Outfit outfit = outfitWith(outfitId);

        when(userOutfitInteractionRepository
                .existsByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.LIKE))
                .thenReturn(false);
        when(userOutfitInteractionRepository
                .existsByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.SAVE))
                .thenReturn(false);
        when(outfitItemQueryService.getById(outfitId)).thenReturn(outfit);
        when(userReferenceQueryService.getReference(userId)).thenReturn(userWith(userId));

        assertThat(service.activate(userId, outfitId, InteractionType.LIKE).active()).isTrue();
        assertThat(service.activate(userId, outfitId, InteractionType.SAVE).active()).isTrue();

        ArgumentCaptor<UserOutfitInteraction> captor = ArgumentCaptor.forClass(UserOutfitInteraction.class);
        verify(userOutfitInteractionRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(UserOutfitInteraction::getInteractionType)
                .containsExactly(InteractionType.LIKE, InteractionType.SAVE);
        assertThat(captor.getAllValues()).extracting(UserOutfitInteraction::getOutfit)
                .containsExactly(outfit, outfit);
    }

    @Test
    void activate_sameOutfitDifferentUsers_bothPersistIndependently() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();
        User firstUser = userWith(firstUserId);
        User secondUser = userWith(secondUserId);

        when(userOutfitInteractionRepository
                .existsByUserIdAndOutfitIdAndInteractionType(firstUserId, outfitId, InteractionType.SAVE))
                .thenReturn(false);
        when(userOutfitInteractionRepository
                .existsByUserIdAndOutfitIdAndInteractionType(secondUserId, outfitId, InteractionType.SAVE))
                .thenReturn(false);
        when(outfitItemQueryService.getById(outfitId)).thenReturn(outfitWith(outfitId));
        when(userReferenceQueryService.getReference(firstUserId)).thenReturn(firstUser);
        when(userReferenceQueryService.getReference(secondUserId)).thenReturn(secondUser);

        service.activate(firstUserId, outfitId, InteractionType.SAVE);
        service.activate(secondUserId, outfitId, InteractionType.SAVE);

        ArgumentCaptor<UserOutfitInteraction> captor = ArgumentCaptor.forClass(UserOutfitInteraction.class);
        verify(userOutfitInteractionRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(UserOutfitInteraction::getUser)
                .containsExactly(firstUser, secondUser);
    }

    @Test
    void activate_calledTwiceSequentially_secondCallIsNoOpNotDuplicateInsert() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();

        when(userOutfitInteractionRepository
                .existsByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.LIKE))
                .thenReturn(false)
                .thenReturn(true);
        when(outfitItemQueryService.getById(outfitId)).thenReturn(outfitWith(outfitId));
        when(userReferenceQueryService.getReference(userId)).thenReturn(userWith(userId));

        assertThat(service.activate(userId, outfitId, InteractionType.LIKE).active()).isTrue();
        assertThat(service.activate(userId, outfitId, InteractionType.LIKE).active()).isTrue();

        verify(userOutfitInteractionRepository, times(1)).saveAndFlush(any());
    }

    // ---------- deactivate ----------

    @Test
    void deactivate_activeInteraction_deletesRowAndReturnsActiveFalse() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();

        when(userOutfitInteractionRepository
                .deleteByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.LIKE))
                .thenReturn(1);

        InteractionStateResponse response = service.deactivate(userId, outfitId, InteractionType.LIKE);

        assertThat(response.active()).isFalse();
        verify(userOutfitInteractionRepository)
                .deleteByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.LIKE);
    }

    @Test
    void deactivate_alreadyInactive_noOpReturnsActiveFalseWithoutError() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();

        when(userOutfitInteractionRepository
                .deleteByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.SAVE))
                .thenReturn(0);

        assertThat(service.deactivate(userId, outfitId, InteractionType.SAVE).active()).isFalse();
    }

    @Test
    void deactivate_neverActivated_sameNoOpBehaviorAsAlreadyInactive() {
        UUID userId = UUID.randomUUID();
        UUID neverTouchedOutfitId = UUID.randomUUID();

        when(userOutfitInteractionRepository
                .deleteByUserIdAndOutfitIdAndInteractionType(userId, neverTouchedOutfitId, InteractionType.LIKE))
                .thenReturn(0);

        InteractionStateResponse response = service.deactivate(userId, neverTouchedOutfitId, InteractionType.LIKE);

        assertThat(response.active()).isFalse();
        verify(userOutfitInteractionRepository, times(1))
                .deleteByUserIdAndOutfitIdAndInteractionType(userId, neverTouchedOutfitId, InteractionType.LIKE);
    }

    @Test
    void deactivate_likeOnly_doesNotAffectSaveOnSameOutfit() {
        UUID userId = UUID.randomUUID();
        UUID outfitId = UUID.randomUUID();

        when(userOutfitInteractionRepository
                .deleteByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.LIKE))
                .thenReturn(1);

        service.deactivate(userId, outfitId, InteractionType.LIKE);

        verify(userOutfitInteractionRepository, never())
                .deleteByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.SAVE);
        verify(userOutfitInteractionRepository, never())
                .deleteByUserIdAndOutfitIdAndInteractionType(userId, outfitId, InteractionType.SHARE);
    }

    // ---------- listSaved ----------

    @Test
    void listSaved_returnsOnlySavedInteractionType_excludesLikedAndSharedOutfits() {
        UUID userId = UUID.randomUUID();
        UUID savedOutfitId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        stubSavedPage(userId, pageable, List.of(outfitWith(savedOutfitId)), 1);
        stubEmptyItemLookups(List.of(savedOutfitId));

        SavedOutfitsResponse response = service.listSaved(userId, pageable);

        assertThat(response.items()).extracting(r -> r.outfitId()).containsExactly(savedOutfitId);
        verify(userOutfitInteractionRepository)
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(userId, InteractionType.SAVE, pageable);
        verify(userOutfitInteractionRepository, never())
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(any(), org.mockito.ArgumentMatchers.eq(InteractionType.LIKE), any());
        verify(userOutfitInteractionRepository, never())
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(any(), org.mockito.ArgumentMatchers.eq(InteractionType.SHARE), any());
    }

    @Test
    void listSaved_ordersByMostRecentlySavedFirst() {
        UUID userId = UUID.randomUUID();
        UUID newestOutfitId = UUID.randomUUID();
        UUID middleOutfitId = UUID.randomUUID();
        UUID oldestOutfitId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        stubSavedPage(userId, pageable,
                List.of(outfitWith(newestOutfitId), outfitWith(middleOutfitId), outfitWith(oldestOutfitId)), 3);
        stubEmptyItemLookups(List.of(newestOutfitId, middleOutfitId, oldestOutfitId));

        SavedOutfitsResponse response = service.listSaved(userId, pageable);

        assertThat(response.items()).extracting(r -> r.outfitId())
                .containsExactly(newestOutfitId, middleOutfitId, oldestOutfitId);
    }

    @Test
    void listSaved_emptyResult_returnsEmptyItemsWithCorrectPaginationMetadataNotError() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        when(userOutfitInteractionRepository
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(userId, InteractionType.SAVE, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        SavedOutfitsResponse response = service.listSaved(userId, pageable);

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void listSaved_emptyResult_neverCallsBatchedItemOrPriceLookups() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        when(userOutfitInteractionRepository
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(userId, InteractionType.SAVE, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.listSaved(userId, pageable);

        verify(outfitItemQueryService, never()).findItemViewsForOutfits(anyList());
        verify(outfitItemQueryService, never()).sumBasePriceForOutfits(anyList());
    }

    @Test
    void listSaved_singlePage_paginationMetadataMatchesTotalElementsAndTotalPages() {
        UUID userId = UUID.randomUUID();
        UUID firstOutfitId = UUID.randomUUID();
        UUID secondOutfitId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        stubSavedPage(userId, pageable, List.of(outfitWith(firstOutfitId), outfitWith(secondOutfitId)), 2);
        stubEmptyItemLookups(List.of(firstOutfitId, secondOutfitId));

        SavedOutfitsResponse response = service.listSaved(userId, pageable);

        assertThat(response.items()).hasSize(2);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void listSaved_pageBeyondLastPage_returnsEmptyItemsNotError() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(5, 20);

        when(userOutfitInteractionRepository
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(userId, InteractionType.SAVE, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 3));

        SavedOutfitsResponse response = service.listSaved(userId, pageable);

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void listSaved_outfitLikedAndSavedByOtherUsers_onlyCallingUsersSavesAppear() {
        UUID callingUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID ownSavedOutfitId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        stubSavedPage(callingUserId, pageable, List.of(outfitWith(ownSavedOutfitId)), 1);
        stubEmptyItemLookups(List.of(ownSavedOutfitId));

        SavedOutfitsResponse response = service.listSaved(callingUserId, pageable);

        assertThat(response.items()).extracting(r -> r.outfitId()).containsExactly(ownSavedOutfitId);

        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(userOutfitInteractionRepository).findByUserIdAndInteractionTypeOrderByCreatedAtDesc(
                userIdCaptor.capture(), org.mockito.ArgumentMatchers.eq(InteractionType.SAVE), any());
        assertThat(userIdCaptor.getValue()).isEqualTo(callingUserId).isNotEqualTo(otherUserId);
    }

    // ---------- listInteractedOutfitIds ----------

    @Test
    void listInteractedOutfitIds_returnsFullSetAcrossMoreItemsThanOnePageWouldHold() {
        UUID userId = UUID.randomUUID();
        List<UUID> manyIds = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            manyIds.add(UUID.randomUUID());
        }

        when(userOutfitInteractionRepository.findOutfitIdsByUserIdAndInteractionType(userId, InteractionType.LIKE))
                .thenReturn(manyIds);

        Set<UUID> result = service.listInteractedOutfitIds(userId, InteractionType.LIKE);

        assertThat(result).hasSize(150);
        assertThat(result).containsExactlyInAnyOrderElementsOf(manyIds);
    }

    @Test
    void listInteractedOutfitIds_noInteractions_returnsEmptySetNotNull() {
        UUID userId = UUID.randomUUID();

        when(userOutfitInteractionRepository.findOutfitIdsByUserIdAndInteractionType(userId, InteractionType.SAVE))
                .thenReturn(List.of());

        Set<UUID> result = service.listInteractedOutfitIds(userId, InteractionType.SAVE);

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void listInteractedOutfitIds_likeType_excludesOutfitsOnlySaved() {
        UUID userId = UUID.randomUUID();
        UUID likedOutfitId = UUID.randomUUID();
        UUID savedOutfitId = UUID.randomUUID();

        when(userOutfitInteractionRepository.findOutfitIdsByUserIdAndInteractionType(userId, InteractionType.LIKE))
                .thenReturn(List.of(likedOutfitId));
        when(userOutfitInteractionRepository.findOutfitIdsByUserIdAndInteractionType(userId, InteractionType.SAVE))
                .thenReturn(List.of(savedOutfitId));

        assertThat(service.listInteractedOutfitIds(userId, InteractionType.LIKE))
                .containsExactly(likedOutfitId)
                .doesNotContain(savedOutfitId);
        assertThat(service.listInteractedOutfitIds(userId, InteractionType.SAVE))
                .containsExactly(savedOutfitId)
                .doesNotContain(likedOutfitId);
    }

    // ---------- fixtures ----------

    private void stubSavedPage(UUID userId, Pageable pageable, List<Outfit> outfits, long totalElements) {
        List<UserOutfitInteraction> interactions = new ArrayList<>();
        LocalDateTime createdAt = LocalDateTime.now();
        for (Outfit outfit : outfits) {
            interactions.add(UserOutfitInteraction.builder()
                    .id(UUID.randomUUID())
                    .createdAt(createdAt)
                    .outfit(outfit)
                    .interactionType(InteractionType.SAVE)
                    .build());
            createdAt = createdAt.minusMinutes(1);
        }
        Page<UserOutfitInteraction> page = new PageImpl<>(interactions, pageable, totalElements);
        when(userOutfitInteractionRepository
                .findByUserIdAndInteractionTypeOrderByCreatedAtDesc(userId, InteractionType.SAVE, pageable))
                .thenReturn(page);
    }

    private void stubEmptyItemLookups(List<UUID> outfitIds) {
        Map<UUID, List<OutfitItemView>> views = new java.util.HashMap<>();
        Map<UUID, BigDecimal> totals = new java.util.HashMap<>();
        for (UUID outfitId : outfitIds) {
            views.put(outfitId, List.of(new OutfitItemView(UUID.randomUUID(), UUID.randomUUID(),
                    "Product", "https://example.com/p.jpg", new BigDecimal("10.00"), GarmentRole.TOP, false)));
            totals.put(outfitId, new BigDecimal("10.00"));
        }
        when(outfitItemQueryService.findItemViewsForOutfits(outfitIds)).thenReturn(views);
        when(outfitItemQueryService.sumBasePriceForOutfits(outfitIds)).thenReturn(totals);
    }

    private Outfit outfitWith(UUID outfitId) {
        return Outfit.builder()
                .id(outfitId)
                .colorScore(new BigDecimal("0.9000"))
                .layeringScore(new BigDecimal("0.8000"))
                .structuredScore(new BigDecimal("0.8500"))
                .embeddingScore(new BigDecimal("0.7000"))
                .compatibilityScore(new BigDecimal("0.8200"))
                .build();
    }

    private User userWith(UUID userId) {
        return User.builder().id(userId).email("user-" + userId + "@example.com").build();
    }
}