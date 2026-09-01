package com.platform.controller;

import com.platform.dto.business.BusinessFeatureDTO;
import com.platform.entity.User;
import com.platform.security.CurrentUser;
import com.platform.service.FeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "Business feature", description = "Feature flags attached to a business")
@RestController
@RequestMapping("/api/business/{businessId}/features")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BusinessFeatureController {

    private final FeatureService featureService;

    @Operation(summary = "List a business's features",
            description = "Returns every feature currently enabled on the business")
    @ApiResponse(responseCode = "200", description = "Features retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @GetMapping
    public ResponseEntity<Set<BusinessFeatureDTO>> getAll(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(featureService.getAllFeatures(businessId));
    }

    @Operation(summary = "Add a feature",
            description = "Enables a feature on the business. The businessId in the body must match the path.")
    @ApiResponse(responseCode = "200", description = "Feature added successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body, or the body's businessId "
            + "does not match the path")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner, or the account is disabled")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @ApiResponse(responseCode = "409", description = "The business already has this feature")
    @PostMapping
    public ResponseEntity<BusinessFeatureDTO> addFeature(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Valid @RequestBody BusinessFeatureDTO request,
            @CurrentUser User currentUser) {
        return ResponseEntity.ok(featureService.addFeature(businessId, request, currentUser));
    }

    @Operation(summary = "Remove a feature",
            description = "Disables a feature on the business")
    @ApiResponse(responseCode = "204", description = "Feature removed successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner")
    @ApiResponse(responseCode = "404", description = "Business or feature not found")
    @DeleteMapping("/{featureId}")
    public ResponseEntity<Void> removeFeature(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Feature ID", example = "1")
            @PathVariable Long featureId,
            @CurrentUser User currentUser) {
        featureService.removeFeature(businessId, featureId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
