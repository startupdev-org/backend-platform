package com.platform.storage;

import com.platform.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;

/**
 * Cloudflare R2 storage, accessed through its S3-compatible API.
 *
 * <p>Uploads go browser-to-bucket through a presigned URL; no image bytes ever pass
 * through this application. That keeps a small Render instance out of the file-transfer
 * business, and it is why the size limit cannot be enforced here - it is advisory only,
 * re-checked at attach time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "r2", matchIfMissing = true)
public class R2StorageProvider implements StorageProvider {

    private final StorageProperties properties;
    private final S3Client r2Client;
    private final S3Presigner r2Presigner;

    @Override
    public UploadTarget createUploadTarget(String key, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
//                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(properties.getSignedUrlTtlSeconds()))
                    .putObjectRequest(putRequest)
                    .build();

            PresignedPutObjectRequest presigned = r2Presigner.presignPutObject(presignRequest);

            return new UploadTarget(
                    presigned.url().toString(),
                    key,
                    properties.getSignedUrlTtlSeconds(),
                    properties.getMaxUploadBytes());
        } catch (Exception e) {
            log.warn("Failed to create upload target for key {}: {}", key, e.getMessage());
            throw new StorageException("Could not create an upload URL");
        }
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse response = r2Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .build());
            return Optional.of(new StoredObject(response.contentLength(), response.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            log.warn("HEAD failed for key {}: {}", key, e.getMessage());
            throw new StorageException("Could not verify the uploaded file");
        } catch (Exception e) {
            log.warn("HEAD failed for key {}: {}", key, e.getMessage());
            throw new StorageException("Could not verify the uploaded file");
        }
    }

    @Override
    public void delete(String key) {
        try {
            // S3-compatible delete is idempotent - a missing key is not an error - so
            // "already gone" is naturally the success path here, no special-casing needed.
            r2Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete key {}: {}", key, e.getMessage());
            throw new StorageException("Could not delete the stored file");
        }
    }

    @Override
    public String toPublicUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        // Rows written before this feature hold a full URL rather than a key. Passing them
        // through keeps those images rendering without a risky data migration.
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        return properties.getR2().getPublicUrlBase() + "/" + key;
    }

    private String bucket() {
        return properties.getBucket();
    }
}
