package com.sewlect.social.repository;

import com.sewlect.social.entity.UserOutfitInteraction;
import com.sewlect.social.enums.InteractionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserOutfitInteractionRepository extends JpaRepository<UserOutfitInteraction, UUID> {

    boolean existsByUserIdAndOutfitIdAndInteractionType(UUID userId, UUID outfitId, InteractionType interactionType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserOutfitInteraction i
            WHERE i.user.id = :userId AND i.outfit.id = :outfitId AND i.interactionType = :interactionType
            """)
    int deleteByUserIdAndOutfitIdAndInteractionType(@Param("userId") UUID userId, @Param("outfitId") UUID outfitId,
                                                    @Param("interactionType") InteractionType interactionType);

    @Query(value = """
            SELECT i FROM UserOutfitInteraction i
            JOIN FETCH i.outfit
            WHERE i.user.id = :userId AND i.interactionType = :interactionType
            ORDER BY i.createdAt DESC, i.id DESC
            """,
            countQuery = """
                    SELECT COUNT(i) FROM UserOutfitInteraction i
                    WHERE i.user.id = :userId AND i.interactionType = :interactionType
                    """)
    Page<UserOutfitInteraction> findByUserIdAndInteractionTypeOrderByCreatedAtDesc(
            @Param("userId") UUID userId,
            @Param("interactionType") InteractionType interactionType,
            Pageable pageable);

    @Query("SELECT i.outfit.id FROM UserOutfitInteraction i "
            + "WHERE i.user.id = :userId AND i.interactionType = :interactionType")
    List<UUID> findOutfitIdsByUserIdAndInteractionType(@Param("userId") UUID userId,
                                                       @Param("interactionType") InteractionType interactionType);

    @Query("SELECT i.outfit.id AS outfitId, COUNT(i) AS interactionCount FROM UserOutfitInteraction i "
            + "WHERE i.outfit.id IN :outfitIds AND i.interactionType = :interactionType GROUP BY i.outfit.id")
    List<OutfitInteractionCount> countByOutfitIdsAndInteractionType(@Param("outfitIds") Collection<UUID> outfitIds,
                                                                    @Param("interactionType") InteractionType interactionType);

    @Query("SELECT i.outfit.id AS outfitId, i.interactionType AS interactionType FROM UserOutfitInteraction i "
            + "WHERE i.user.id = :userId AND i.outfit.id IN :outfitIds")
    List<ViewerInteraction> findViewerInteractions(@Param("userId") UUID userId,
                                                   @Param("outfitIds") Collection<UUID> outfitIds);

    interface OutfitInteractionCount {
        UUID getOutfitId();

        long getInteractionCount();
    }

    interface ViewerInteraction {
        UUID getOutfitId();

        InteractionType getInteractionType();
    }

}