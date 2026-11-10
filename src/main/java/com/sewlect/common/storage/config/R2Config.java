package com.sewlect.common.storage.config;

import com.sewlect.common.storage.properties.R2Properties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class R2Config {

    private static final Region R2_REGION = Region.of("auto");

    private static final boolean PATH_STYLE_ACCESS = true;

    private final R2Properties properties;

    public R2Config(R2Properties properties) {
        this.properties = properties;
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
    }

    private S3Configuration serviceConfiguration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(PATH_STYLE_ACCESS)
                .build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(R2_REGION)
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(serviceConfiguration())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(R2_REGION)
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(serviceConfiguration())
                .build();
    }
}