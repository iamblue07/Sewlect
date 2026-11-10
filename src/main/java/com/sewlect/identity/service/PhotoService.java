package com.sewlect.identity.service;

import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.storage.util.StorageKeys;
import com.sewlect.common.storage.service.StorageService;
import com.sewlect.identity.dto.PresignedUploadResponse;
import com.sewlect.identity.dto.UserPhotoResponse;
import com.sewlect.identity.enums.PhotoType;
import com.sewlect.identity.entity.UserBodyPhoto;
import com.sewlect.identity.repository.UserBodyPhotoRepository;
import com.sewlect.identity.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PhotoService {

    private static final String BODY_PHOTO_CONTENT_TYPE = "image/jpeg";

    private final UserBodyPhotoRepository userBodyPhotoRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final Clock clock;

    public PresignedUploadResponse generateUploadUrl(UUID userId, PhotoType photoType) {
        String key = StorageKeys.bodyPhotoKey(userId, photoType.name().toLowerCase());

        URI uploadUrl = storageService.generateUploadUrl(key, BODY_PHOTO_CONTENT_TYPE, StorageService.DEFAULT_TTL);
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(StorageService.DEFAULT_TTL);

        return new PresignedUploadResponse(uploadUrl.toString(), expiresAt);
    }

    public UserPhotoResponse confirmUpload(UUID userId, PhotoType photoType) {
        String key = StorageKeys.bodyPhotoKey(userId, photoType.name().toLowerCase());

        if (!storageService.exists(key)) {
            throw new BadRequestException("No upload found for photo type: " + photoType);
        }

        if (userBodyPhotoRepository.recordUpload(userId, photoType, key, LocalDateTime.now(clock)) == 0) {
            userBodyPhotoRepository.save(newPhoto(userId, photoType, key));
        }

        return toPhotoResponse(photoType, key);
    }

    @Transactional(readOnly = true)
    public List<UserPhotoResponse> listPhotos(UUID userId) {
        return userBodyPhotoRepository.findAllByUserId(userId).stream()
                .map(photo -> toPhotoResponse(photo.getPhotoType(), photo.getStorageKey()))
                .toList();
    }

    public String getStorageKey(UUID userId, PhotoType photoType) {
        return userBodyPhotoRepository.findByUserIdAndPhotoType(userId, photoType)
                .map(UserBodyPhoto::getStorageKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No " + photoType.name().toLowerCase() + " body photo found for user " + userId));
    }

    public Optional<LocalDateTime> getLastModifiedAt(UUID userId, PhotoType photoType) {
        return userBodyPhotoRepository.findByUserIdAndPhotoType(userId, photoType)
                .map(photo -> photo.getUpdatedAt() != null ? photo.getUpdatedAt() : photo.getCreatedAt());
    }

    private UserBodyPhoto newPhoto(UUID userId, PhotoType photoType, String storageKey) {
        return UserBodyPhoto.builder()
                .user(userRepository.getReferenceById(userId))
                .photoType(photoType)
                .storageKey(storageKey)
                .build();
    }

    private UserPhotoResponse toPhotoResponse(PhotoType photoType, String storageKey) {
        URI downloadUrl = storageService.generateDownloadUrl(storageKey, StorageService.DEFAULT_TTL);
        return new UserPhotoResponse(photoType, downloadUrl.toString());
    }
}