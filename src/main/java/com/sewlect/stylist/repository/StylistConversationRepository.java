package com.sewlect.stylist.repository;

import com.sewlect.stylist.entity.StylistConversation;
import com.sewlect.stylist.enums.ConversationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StylistConversationRepository extends JpaRepository<StylistConversation, UUID> {

    Optional<StylistConversation> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
            SELECT c FROM StylistConversation c
            WHERE c.user.id = :userId
            ORDER BY c.updatedAt DESC, c.id DESC
            """,
            countQuery = "SELECT COUNT(c) FROM StylistConversation c WHERE c.user.id = :userId")
    Page<StylistConversation> findPageByUserId(@Param("userId") UUID userId, Pageable pageable);

    List<StylistConversation> findByStatusAndUpdatedAtBefore(ConversationStatus status, LocalDateTime cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE StylistConversation c SET c.updatedAt = :now WHERE c.id = :id AND c.status = :expected")
    int touch(@Param("id") UUID id,
              @Param("expected") ConversationStatus expected,
              @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE StylistConversation c SET c.title = :title, c.updatedAt = :now
            WHERE c.id = :id AND c.status = :expected
            """)
    int touchAndRetitle(@Param("id") UUID id,
                        @Param("expected") ConversationStatus expected,
                        @Param("title") String title,
                        @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE StylistConversation c SET c.status = :target, c.updatedAt = :now
            WHERE c.id = :id AND c.status = :expected
            """)
    int transitionStatus(@Param("id") UUID id,
                         @Param("expected") ConversationStatus expected,
                         @Param("target") ConversationStatus target,
                         @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE StylistConversation c SET c.status = :target, c.completedAt = :now, c.updatedAt = :now
            WHERE c.id = :id AND c.status = :expected
            """)
    int completeIfStatus(@Param("id") UUID id,
                         @Param("expected") ConversationStatus expected,
                         @Param("target") ConversationStatus target,
                         @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM StylistConversation c WHERE c.id = :id AND c.status <> :protectedStatus")
    int deleteUnlessStatus(@Param("id") UUID id, @Param("protectedStatus") ConversationStatus protectedStatus);
}
