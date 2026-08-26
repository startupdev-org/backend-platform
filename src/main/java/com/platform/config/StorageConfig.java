package com.platform.config;

import com.platform.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    /**
     * R2 speaks the S3 API but is not AWS - "auto" is the region R2 expects, and the
     * endpoint is overridden to the account's R2 endpoint instead of an AWS region endpoint.
     */
    @Bean
    public S3Client r2Client(StorageProperties properties) {
        return S3Client.builder()
                .region(Region.of("auto"))
                .endpointOverride(r2Endpoint(properties))
                .credentialsProvider(credentialsProvider(properties))
                .build();
    }

    @Bean
    public S3Presigner r2Presigner(StorageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of("auto"))
                .endpointOverride(r2Endpoint(properties))
                .credentialsProvider(credentialsProvider(properties))
                .build();
    }

    private URI r2Endpoint(StorageProperties properties) {
        return URI.create("https://" + properties.getR2().getAccountId() + ".r2.cloudflarestorage.com");
    }

    private StaticCredentialsProvider credentialsProvider(StorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getR2().getAccessKeyId(),
                properties.getR2().getSecretAccessKey()));
    }
}
