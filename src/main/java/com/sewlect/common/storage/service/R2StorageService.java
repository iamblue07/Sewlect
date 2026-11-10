package com.sewlect.common.storage.service;

import com.sewlect.common.exception.ExternalServiceException;
import com.sewlect.common.storage.properties.R2Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.function.Supplier;

@Service
public class R2StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(200);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2Properties properties;

    public R2StorageService(S3Client s3Client, S3Presigner s3Presigner, R2Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public URI generateUploadUrl(String key, String contentType, Duration ttl) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(objectRequest)
                .build();

        try {
            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            return toUri(presigned.url());
        } catch (SdkException e) {
            log.error("Failed to generate R2 upload URL for key: {}", key, e);
            throw new ExternalServiceException("Failed to generate upload URL for key: " + key);
        }
    }

    @Override
    public URI generateDownloadUrl(String key, Duration ttl) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(objectRequest)
                .build();

        try {
            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
            return toUri(presigned.url());
        } catch (SdkException e) {
            log.error("Failed to generate R2 download URL for key: {}", key, e);
            throw new ExternalServiceException("Failed to generate download URL for key: " + key);
        }
    }

    @Override
    public void store(String key, byte[] bytes, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();

        try {
            withRetry("upload", key, () -> s3Client.putObject(request, RequestBody.fromBytes(bytes)));
        } catch (SdkException e) {
            log.error("Failed to upload object to R2 for key: {}", key, e);
            throw new ExternalServiceException("Failed to upload object for key: " + key);
        }
    }

    @Override
    public boolean exists(String key) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();

        try {
            withRetry("existence check", key, () -> s3Client.headObject(request));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.error("Failed to check existence of R2 object for key: {}", key, e);
            throw new ExternalServiceException("Failed to check existence of object for key: " + key);
        } catch (SdkException e) {
            log.error("Failed to check existence of R2 object for key: {}", key, e);
            throw new ExternalServiceException("Failed to check existence of object for key: " + key);
        }
    }

    @Override
    public boolean delete(String key) {
        if (!exists(key)) {
            return false;
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();

        try {
            withRetry("deletion", key, () -> s3Client.deleteObject(request));
            return true;
        } catch (SdkException e) {
            log.error("Failed to delete R2 object for key: {}", key, e);
            throw new ExternalServiceException("Failed to delete object for key: " + key);
        }
    }

    private URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new ExternalServiceException("R2 returned a malformed presigned URL");
        }
    }

    private <T> T withRetry(String operation, String key, Supplier<T> action) {
        SdkException lastException;
        int attempt = 1;

        while (true) {
            try {
                return action.get();
            } catch (SdkException e) {
                lastException = e;

                if (!isRetryable(e) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }

                Duration backoff = INITIAL_BACKOFF.multipliedBy(1L << (attempt - 1));
                log.warn("R2 {} failed for key: {} (attempt {}/{}), retrying in {}ms",
                        operation, key, attempt, MAX_ATTEMPTS, backoff.toMillis(), e);

                try {
                    Thread.sleep(backoff.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw lastException;
                }

                attempt++;
            }
        }
    }

    private boolean isRetryable(SdkException e) {
        if (e instanceof S3Exception s3Exception) {
            return s3Exception.statusCode() >= 500;
        }
        return true;
    }
}