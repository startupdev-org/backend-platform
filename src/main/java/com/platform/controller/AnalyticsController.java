package com.platform.controller;

import com.platform.dto.analytics.BusinessDashboardDTO;
import com.platform.entity.User;
import com.platform.service.AnalyticsService;
import com.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Analytics", description = "Owner-facing business dashboard")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService userService;

    @Operation(summary = "Get a business dashboard",
            description = "Returns the aggregate counts and revenue for one business. Owner-only: "
                    + "a caller who does not own the business is told it does not exist rather "
                    + "than that access is forbidden.")
    @ApiResponse(responseCode = "200", description = "Dashboard retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Caller is not a business or platform administrator")
    @ApiResponse(responseCode = "404", description = "Business not found, or it belongs to another owner")
    @GetMapping("/business/{businessId}/dashboard")
    public ResponseEntity<BusinessDashboardDTO> getBusinessDashboard(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(analyticsService.getBusinessDashboard(businessId, currentUser));
    }
}
