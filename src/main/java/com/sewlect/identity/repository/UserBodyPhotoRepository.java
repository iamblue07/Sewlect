package com.sewlect.identity.repository;

import com.sewlect.identity.enums.PhotoType;
import com.sewlect.identity.entity.UserBodyPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBodyPhotoRepository extends JpaRepository<UserBodyPhoto, UUID> {

    Optional<UserBodyPhoto> findByUserIdAndPhotoType(UUID userId, PhotoType photoType);
    List<UserBodyPhoto> findAllByUserId(UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserBodyPhoto p SET p.storageKey = :storageKey, p.updatedAt = :uploadedAt
            WHERE p.user.id = :userId AND p.photoType = :photoType
            """)
    int recordUpload(@Param("userId") UUID userId, @Param("photoType") PhotoType photoType,
                     @Param("storageKey") String storageKey, @Param("uploadedAt") LocalDateTime uploadedAt);
}
