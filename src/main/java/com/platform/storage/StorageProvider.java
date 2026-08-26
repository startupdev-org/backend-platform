package com.platform.storage;

import java.util.Optional;

/**
 * The only thing in the application that knows which object store is in use.
 *
 * <p>Nothing outside {@code com.platform.storage} references a concrete provider, so
 * swapping Supabase for R2 or Cloudinary later is one new implementation plus one
 * config value.
 */
public interface StorageProvider extends ImageUrlResolver {

    /** Mint a short-lived URL the browser can PUT one object to. */
    UploadTarget createUploadTarget(String key, String contentType);

    /** Size and content type of an object, or empty when it does not exist. */
    Optional<StoredObject> head(String key);

    /** Remove an object. Implementations treat "already gone" as success. */
    void delete(String key);
}
