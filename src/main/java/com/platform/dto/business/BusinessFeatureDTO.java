package com.platform.dto.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BusinessFeatureDTO {

    private Long featureId;

    // Deliberately unconstrained: the field is echoed back on responses, and on
    // the create path the business is identified by the {businessId} in the URL.
    private UUID businessId;

    @NotBlank(message = "Feature name is required")
    @Size(min = 2, max = 100, message = "Feature name must be between 2 and 100 characters")
    private String name;
}
