package com.sewlect.common.storage.service;

import java.net.URI;
import java.time.Duration;

public interface StorageService {

    Duration DEFAULT_TTL = Duration.ofMinutes(15);

    URI generateUploadUrl(String key, String contentType, Duration ttl);

    URI generateDownloadUrl(String key, Duration ttl);

    void store(String key, byte[] bytes, String contentType);

    boolean exists(String key);

    boolean delete(String key);
}