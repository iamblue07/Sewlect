package com.sewlect.identity.service;

import com.sewlect.common.exception.BadRequestException;
import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.common.exception.ResourceNotFoundException;
import com.sewlect.common.storage.util.StorageKeys;
import com.sewlect.common.storage.service.StorageService;
import com.sewlect.identity.dto.PresignedUploadResponse;
import com.sewlect.identity.dto.UserPhotoResponse;
import com.sewlect.identity.enums.PhotoType;
import com.sewlect.identity.entity.User;
import com.sewlect.identity.entity.UserBodyPhoto;
import com.sewlect.identity.repository.UserBodyPhotoRepository;
import com.sewlect.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.mockito.Spy;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock
    private UserBodyPhotoRepository userBodyPhotoRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private PhotoService photoService;

    @Test
    void generateUploadUrl_buildsKeyViaStorageKeysAndDelegatesToStorageService() {
        UUID userId = UUID.randomUUID();
        String expectedKey = StorageKeys.bodyPhotoKey(userId, "front");
        URI presignedUrl = URI.create("https://r2.example.com/presigned-put");

        when(storageService.generateUploadUrl(expectedKey, "image/jpeg", StorageService.DEFAULT_TTL))
                .thenReturn(presignedUrl);

        PresignedUploadResponse response = photoService.generateUploadUrl(userId, PhotoType.FRONT);

        assertThat(response.uploadUrl()).isEqualTo(presignedUrl.toString());
        assertThat(response.expiresAt())
                .isCloseTo(LocalDateTime.now().plus(StorageService.DEFAULT_TTL), within(5, ChronoUnit.SECONDS));

        verify(storageService).generateUploadUrl(expectedKey, "image/jpeg", StorageService.DEFAULT_TTL);
    }

    @Test
    void confirmUpload_objectMissingFromStorage_throwsBadRequestException() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");

        when(storageService.exists(key)).thenReturn(false);

        assertThatThrownBy(() -> photoService.confirmUpload(userId, PhotoType.FRONT))
                .isInstanceOf(BadRequestException.class);

        verify(userBodyPhotoRepository, never()).findByUserIdAndPhotoType(any(), any());
        verify(userBodyPhotoRepository, never()).save(any());
    }

    @Test
    void confirmUpload_newPhotoType_insertsRowWithTheKey() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");
        User userRef = User.builder().id(userId).build();
        URI downloadUrl = URI.create("https://r2.example.com/presigned-get");

        when(storageService.exists(key)).thenReturn(true);
        when(userBodyPhotoRepository.recordUpload(eq(userId), eq(PhotoType.FRONT), eq(key), any(LocalDateTime.class)))
                .thenReturn(0);
        when(userRepository.getReferenceById(userId)).thenReturn(userRef);
        when(storageService.generateDownloadUrl(key, StorageService.DEFAULT_TTL)).thenReturn(downloadUrl);

        UserPhotoResponse response = photoService.confirmUpload(userId, PhotoType.FRONT);

        ArgumentCaptor<UserBodyPhoto> captor = ArgumentCaptor.forClass(UserBodyPhoto.class);
        verify(userBodyPhotoRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(userRef);
        assertThat(captor.getValue().getPhotoType()).isEqualTo(PhotoType.FRONT);
        assertThat(captor.getValue().getStorageKey()).isEqualTo(key);

        assertThat(response.photoType()).isEqualTo(PhotoType.FRONT);
        assertThat(response.downloadUrl()).isEqualTo(downloadUrl.toString());
    }

    @Test
    void confirmUpload_existingPhotoType_updatesRowRatherThanInserting() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");
        URI downloadUrl = URI.create("https://r2.example.com/presigned-get");

        when(storageService.exists(key)).thenReturn(true);
        when(userBodyPhotoRepository.recordUpload(eq(userId), eq(PhotoType.FRONT), eq(key), any(LocalDateTime.class)))
                .thenReturn(1);
        when(storageService.generateDownloadUrl(key, StorageService.DEFAULT_TTL)).thenReturn(downloadUrl);

        UserPhotoResponse response = photoService.confirmUpload(userId, PhotoType.FRONT);

        verify(userBodyPhotoRepository, never()).save(any());
        verify(userRepository, never()).getReferenceById(any());
        assertThat(response.downloadUrl()).isEqualTo(downloadUrl.toString());
    }

    @Test
    void listPhotos_generatesFreshPresignedUrlPerPhoto() {
        UUID userId = UUID.randomUUID();
        String frontKey = StorageKeys.bodyPhotoKey(userId, "front");
        String backKey = StorageKeys.bodyPhotoKey(userId, "back");

        UserBodyPhoto frontPhoto = UserBodyPhoto.builder()
                .user(User.builder().id(userId).build())
                .photoType(PhotoType.FRONT)
                .storageKey(frontKey)
                .build();
        UserBodyPhoto backPhoto = UserBodyPhoto.builder()
                .user(User.builder().id(userId).build())
                .photoType(PhotoType.BACK)
                .storageKey(backKey)
                .build();

        URI frontUrl = URI.create("https://r2.example.com/front-get");
        URI backUrl = URI.create("https://r2.example.com/back-get");

        when(userBodyPhotoRepository.findAllByUserId(userId)).thenReturn(List.of(frontPhoto, backPhoto));
        when(storageService.generateDownloadUrl(frontKey, StorageService.DEFAULT_TTL)).thenReturn(frontUrl);
        when(storageService.generateDownloadUrl(backKey, StorageService.DEFAULT_TTL)).thenReturn(backUrl);

        List<UserPhotoResponse> result = photoService.listPhotos(userId);

        assertThat(result).containsExactlyInAnyOrder(
                new UserPhotoResponse(PhotoType.FRONT, frontUrl.toString()),
                new UserPhotoResponse(PhotoType.BACK, backUrl.toString()));

        verify(storageService).generateDownloadUrl(frontKey, StorageService.DEFAULT_TTL);
        verify(storageService).generateDownloadUrl(backKey, StorageService.DEFAULT_TTL);
    }

    @Test
    void generateUploadUrl_backPhotoType_buildsKeyWithBackSuffix() {
        UUID userId = UUID.randomUUID();
        String expectedKey = StorageKeys.bodyPhotoKey(userId, "back");
        URI presignedUrl = URI.create("https://r2.example.com/presigned-put-back");

        when(storageService.generateUploadUrl(expectedKey, "image/jpeg", StorageService.DEFAULT_TTL))
                .thenReturn(presignedUrl);

        PresignedUploadResponse response = photoService.generateUploadUrl(userId, PhotoType.BACK);

        assertThat(response.uploadUrl()).isEqualTo(presignedUrl.toString());
        verify(storageService).generateUploadUrl(expectedKey, "image/jpeg", StorageService.DEFAULT_TTL);
    }

    @Test
    void confirmUpload_reUpload_restampsTheRowWithTheSameKeyAndTheCurrentTime() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");

        when(storageService.exists(key)).thenReturn(true);
        when(userBodyPhotoRepository.recordUpload(eq(userId), eq(PhotoType.FRONT), eq(key), any(LocalDateTime.class)))
                .thenReturn(1);
        when(storageService.generateDownloadUrl(key, StorageService.DEFAULT_TTL))
                .thenReturn(URI.create("https://r2.example.com/get"));

        photoService.confirmUpload(userId, PhotoType.FRONT);

        ArgumentCaptor<LocalDateTime> uploadedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userBodyPhotoRepository).recordUpload(eq(userId), eq(PhotoType.FRONT), eq(key), uploadedAt.capture());
        assertThat(uploadedAt.getValue()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void listPhotos_noPhotosUploaded_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(userBodyPhotoRepository.findAllByUserId(userId)).thenReturn(List.of());

        List<UserPhotoResponse> result = photoService.listPhotos(userId);

        assertThat(result).isEmpty();
        verify(storageService, never()).generateDownloadUrl(any(), any());
    }

    @Test
    void confirmUpload_storageServiceExistsCheckFails_propagatesExternalServiceException() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");

        when(storageService.exists(key)).thenThrow(new ExternalServiceException("R2 unreachable"));

        assertThatThrownBy(() -> photoService.confirmUpload(userId, PhotoType.FRONT))
                .isInstanceOf(ExternalServiceException.class);

        verify(userBodyPhotoRepository, never()).save(any());
    }

    @Test
    void getStorageKey_photoExists_returnsItsStorageKey() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "front");
        UserBodyPhoto existingPhoto = UserBodyPhoto.builder()
                .user(User.builder().id(userId).build())
                .photoType(PhotoType.FRONT)
                .storageKey(key)
                .build();
        when(userBodyPhotoRepository.findByUserIdAndPhotoType(userId, PhotoType.FRONT))
                .thenReturn(Optional.of(existingPhoto));

        String result = photoService.getStorageKey(userId, PhotoType.FRONT);

        assertThat(result).isEqualTo(key);
    }

    @Test
    void getStorageKey_backPhotoType_looksUpByBackType() {
        UUID userId = UUID.randomUUID();
        String key = StorageKeys.bodyPhotoKey(userId, "back");
        UserBodyPhoto existingPhoto = UserBodyPhoto.builder()
                .user(User.builder().id(userId).build())
                .photoType(PhotoType.BACK)
                .storageKey(key)
                .build();
        when(userBodyPhotoRepository.findByUserIdAndPhotoType(userId, PhotoType.BACK))
                .thenReturn(Optional.of(existingPhoto));

        String result = photoService.getStorageKey(userId, PhotoType.BACK);

        assertThat(result).isEqualTo(key);
        verify(userBodyPhotoRepository, never()).findByUserIdAndPhotoType(userId, PhotoType.FRONT);
    }

    @Test
    void getStorageKey_noPhotoUploadedForType_throwsResourceNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userBodyPhotoRepository.findByUserIdAndPhotoType(userId, PhotoType.FRONT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> photoService.getStorageKey(userId, PhotoType.FRONT))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("front")
                .hasMessageContaining(userId.toString());
    }
}