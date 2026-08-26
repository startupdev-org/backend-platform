package com.platform.dto.image;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Step 1 of the upload flow. The client supplies only the content type - never a file
 * name and never a folder. The storage key is derived server-side from the path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlRequestDTO {

    @NotBlank(message = "Content type is required")
    private String contentType;
}
