package com.platform.storage;

/**
 * Everything the browser needs to upload one image directly to the bucket.
 *
 * <p>{@code maxBytes} is advisory only. In a presigned flow the bytes never reach this
 * application, so the enforceable limit is the one configured on the bucket itself -
 * this value just lets the client reject an oversized file before wasting the upload.
 */
public record UploadTarget(String uploadUrl, String storageKey, int expiresInSeconds, long maxBytes) {
}
