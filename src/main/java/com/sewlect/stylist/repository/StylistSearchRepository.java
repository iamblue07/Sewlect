package com.sewlect.stylist.repository;

import com.sewlect.stylist.entity.StylistSearch;
import com.sewlect.stylist.enums.StylistSearchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StylistSearchRepository extends JpaRepository<StylistSearch, UUID> {

    List<StylistSearch> findByConversationIdOrderByPositionAsc(UUID conversationId);

    List<StylistSearch> findByConversationIdAndStatusOrderByPositionAsc(UUID conversationId, StylistSearchStatus status);

    boolean existsByConversationIdAndStatus(UUID conversationId, StylistSearchStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM StylistSearch s WHERE s.conversation.id = :conversationId AND s.status = :status")
    int deleteByConversationIdAndStatus(@Param("conversationId") UUID conversationId,
                                        @Param("status") StylistSearchStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE StylistSearch s SET s.status = :target, s.aiPromptQueryId = :queryId
            WHERE s.id = :id AND s.status = :expected
            """)
    int recordOutcome(@Param("id") UUID id,
                      @Param("expected") StylistSearchStatus expected,
                      @Param("target") StylistSearchStatus target,
                      @Param("queryId") UUID queryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE StylistSearch s SET s.status = :target
            WHERE s.conversation.id = :conversationId AND s.status = :expected
            """)
    int transitionStatusByConversationId(@Param("conversationId") UUID conversationId,
                                         @Param("expected") StylistSearchStatus expected,
                                         @Param("target") StylistSearchStatus target);
}
