package com.platform.storage;

import com.platform.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Supabase Storage against a <em>public</em> bucket.
 *
 * <p>Uploads go browser-to-bucket through a presigned URL; no image bytes ever pass
 * through this application. That keeps a small Render instance out of the file-transfer
 * business, and it is why the size limit cannot be enforced here - the bucket's own
 * {@code file_size_limit} and {@code allowed_mime_types} are the real gate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "supabase", matchIfMissing = true)
public class SupabaseStorageProvider implements StorageProvider {

    private final StorageProperties properties;
    private final RestTemplate storageRestTemplate;

    @Override
    public UploadTarget createUploadTarget(String key, String contentType) {
        String url = storageApi() + "/object/upload/sign/" + bucket() + "/" + key;

        Map<String, Object> body = Map.of("expiresIn", properties.getSignedUrlTtlSeconds());

        try {
            ResponseEntity<Map> response = storageRestTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, authJsonHeaders()), Map.class);

            Object signedPath = response.getBody() == null ? null : response.getBody().get("url");
            if (signedPath == null) {
                throw new StorageException("Storage provider returned no upload URL");
            }

            return new UploadTarget(
                    storageApi() + signedPath,
                    key,
                    properties.getSignedUrlTtlSeconds(),
                    properties.getMaxUploadBytes());
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to create upload target for key {}: {}", key, e.getMessage());
            throw new StorageException("Could not create an upload URL");
        }
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HttpHeaders headers = storageRestTemplate.headForHeaders(publicObjectUrl(key));
            long length = headers.getContentLength();
            MediaType mediaType = headers.getContentType();
            return Optional.of(new StoredObject(
                    length < 0 ? 0 : length,
                    mediaType == null ? null : mediaType.toString()));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            log.warn("HEAD failed for key {}: {}", key, e.getStatusCode());
            throw new StorageException("Could not verify the uploaded file");
        } catch (Exception e) {
            log.warn("HEAD failed for key {}: {}", key, e.getMessage());
            throw new StorageException("Could not verify the uploaded file");
        }
    }

    @Override
    public void delete(String key) {
        String url = storageApi() + "/object/" + bucket() + "/" + key;
        try {
            storageRestTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), String.class);
        } catch (HttpClientErrorException e) {
            // Already gone is the outcome the caller wanted.
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                log.warn("Failed to delete key {}: {}", key, e.getStatusCode());
                throw new StorageException("Could not delete the stored file");
            }
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
        return publicObjectUrl(key);
    }

    private String publicObjectUrl(String key) {
        return storageApi() + "/object/public/" + bucket() + "/" + key;
    }

    private String storageApi() {
        return properties.getSupabase().getUrl() + "/storage/v1";
    }

    private String bucket() {
        return properties.getBucket();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getSupabase().getServiceKey());
        return headers;
    }

    private HttpHeaders authJsonHeaders() {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
