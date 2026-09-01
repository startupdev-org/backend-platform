package com.platform.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of assigning services to an employee at base price.
 *
 * <p>{@code assignments} is the full resulting set of price rows for the requested
 * services across the business's locations - rows this call created and rows that were
 * already there, so the caller sees the effective price of each. The two counts are what
 * makes the idempotency visible: a repeat call returns the same {@code assignments} with
 * {@code createdCount} at zero.
 */
public record EmployeeServiceAssignmentResponseDTO(
        UUID employeeId,
        int createdCount,
        int alreadyAssignedCount,
        List<EmployeeLocationServicePriceResponseDTO> assignments
) {}
