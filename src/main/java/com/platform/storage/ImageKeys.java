package com.platform.storage;

import com.platform.exception.BadRequestException;

import java.util.Map;
import java.util.UUID;

/**
 * Builds and validates storage keys.
 *
 * <p>Keys are generated here and never assembled from client input. The previous
 * iteration of this feature took a {@code folder} request parameter straight from the
 * caller, which let any authenticated account write into any other business's folder
 * (and walk out of the bucket prefix entirely with {@code ../}). The client now sends
 * only a content type; everything else in the path comes from the authenticated
 * request's own path variables.
 */
public final class ImageKeys {

    /** Content types we accept, mapped to the extension the key gets. */
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private ImageKeys() {
    }

    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && ALLOWED_CONTENT_TYPES.containsKey(normalize(contentType));
    }

    /**
     * @throws BadRequestException when the content type is not an image type we accept
     */
    public static String extensionFor(String contentType) {
        String extension = contentType == null ? null : ALLOWED_CONTENT_TYPES.get(normalize(contentType));
        if (extension == null) {
            throw new BadRequestException(
                    "Unsupported image type. Allowed types: JPEG, PNG, WebP");
        }
        return extension;
    }

    /** {@code business/{businessId}/logo/} - the prefix every logo key must start with. */
    public static String businessPrefix(UUID businessId, ImageTarget target) {
        return "business/" + businessId + "/" + target.folder() + "/";
    }

    /** {@code business/{businessId}/employee/{employeeId}/photo/} */
    public static String employeePhotoPrefix(UUID businessId, UUID employeeId) {
        return "business/" + businessId + "/employee/" + employeeId + "/"
                + ImageTarget.EMPLOYEE_PHOTO.folder() + "/";
    }

    public static String generate(String prefix, String contentType) {
        return prefix + UUID.randomUUID() + "." + extensionFor(contentType);
    }

    /**
     * The guard that makes the two-step flow safe: an attach request may only name a key
     * that this same business and slot would have produced. Without it a caller could
     * attach another tenant's key - or any string at all - to their own row.
     *
     * @throws BadRequestException when the key does not belong to this prefix
     */
    public static void requirePrefix(String key, String expectedPrefix) {
        if (key == null || !key.startsWith(expectedPrefix) || key.length() == expectedPrefix.length()) {
            throw new BadRequestException("Storage key does not belong to this resource");
        }
    }

    private static String normalize(String contentType) {
        // Browsers may send "image/jpeg; charset=binary" - compare on the media type only.
        int separator = contentType.indexOf(';');
        String mediaType = separator < 0 ? contentType : contentType.substring(0, separator);
        return mediaType.trim().toLowerCase();
    }
}
