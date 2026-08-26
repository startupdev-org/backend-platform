package com.platform.storage;

/**
 * Turns a stored image key into a URL a browser can load.
 *
 * <p>Split out from {@link StorageProvider} so the DTO mappers can depend on this one
 * method without dragging the whole provider - and its HTTP client - into the
 * {@code com.platform.dto} package.
 */
@FunctionalInterface
public interface ImageUrlResolver {

    /**
     * @param key a storage key as produced by {@link ImageKeys}, or {@code null}
     * @return the public URL, or {@code null} when the key is null/blank
     */
    String toPublicUrl(String key);
}
