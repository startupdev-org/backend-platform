package com.platform.dto.image;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Step 3: bind an already-uploaded object to the entity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachImageRequestDTO {

    @NotBlank(message = "Storage key is required")
    private String storageKey;
}
