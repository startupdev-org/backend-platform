package com.platform.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record EmployeeLocationServicePriceRequestDTO(
        @NotNull(message = "Employee id is required")
        UUID employeeId,

        @NotNull(message = "Service id is required")
        UUID serviceId,

        @NotNull(message = "Location id is required")
        UUID locationId,

        // Negative prices used to be accepted outright.
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", message = "Price must not be negative")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 2 decimal places")
        BigDecimal price
) {}
