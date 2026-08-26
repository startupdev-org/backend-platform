package com.platform.dto.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlResponseDTO {

    /** PUT the raw file bytes here. Expires quickly - see expiresInSeconds. */
    private String uploadUrl;

    /** Hand this back to the attach endpoint once the upload succeeds. */
    private String storageKey;

    private int expiresInSeconds;

    /** Advisory. The bucket enforces the real limit. */
    private long maxBytes;
}
