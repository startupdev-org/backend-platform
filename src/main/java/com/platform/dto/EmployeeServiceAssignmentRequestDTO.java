package com.platform.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/business/{businessId}/employee/{employeeId}/services}.
 *
 * <p>Deliberately carries no price. This is the convenience path: every row it creates
 * uses the service's own base price. A per-employee, per-location override is a separate,
 * explicit act - {@code POST|PUT /api/business/{businessId}/employee-service-price}.
 */
public record EmployeeServiceAssignmentRequestDTO(

        @NotEmpty(message = "At least one service id is required")
        List<@NotNull(message = "Service id must not be null") UUID> serviceIds
) {}
