package com.platform.dto.analytics;

import lombok.Builder;

import java.util.UUID;

/**
 * Aggregate counters for a single business's owner dashboard.
 *
 * <p>Replaces the untyped {@code Map<String, Object>} the endpoint used to
 * return, so the response contract is visible in the code and in Swagger.
 */
@Builder
public record BusinessDashboardDTO(
        UUID businessId,
        long totalBookings,
        double averageRating,
        long totalReviews
) {}
