package com.sewlect.identity.repository;

import com.sewlect.identity.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :revokedAt WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :revokedAt WHERE t.id = :id AND t.revokedAt IS NULL")
    int revokeIfActive(@Param("id") UUID id, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
