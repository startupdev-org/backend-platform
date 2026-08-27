package com.platform.controller;

import com.platform.dto.business.CreateWorkingHoursRequest;
import com.platform.dto.business.BusinessWorkingHoursDTO;
import com.platform.entity.User;
import com.platform.service.BusinessWorkingHoursService;
import com.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * One row per opening interval per weekday. {@code AvailabilityService} reads these rows to
 * work out bookable slots, so a weekday with no row is closed, not open on a default grid.
 */
@Tag(name = "Working hours", description = "Business opening hours, one row per weekday interval")
@RestController
@RequestMapping("/api/business/{businessId}/working-hours")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BusinessWorkingHoursController {

    private final BusinessWorkingHoursService service;
    private final UserService userService;

    @Operation(summary = "Add a working-hours interval",
            description = "Adds one opening interval for one weekday. Only the business owner may call this.")
    @ApiResponse(responseCode = "200", description = "Working hours created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body, or the open time is not before the close time")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @ApiResponse(responseCode = "409", description = "The interval overlaps an existing one for that weekday")
    @PostMapping
    public ResponseEntity<BusinessWorkingHoursDTO> create(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateWorkingHoursRequest request,
            Authentication authentication) {

        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(
                service.create(businessId, request, currentUser)
        );
    }

    @Operation(summary = "List a business's working hours",
            description = "Returns every opening interval of the business, across all weekdays")
    @ApiResponse(responseCode = "200", description = "Working hours retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @GetMapping
    public ResponseEntity<List<BusinessWorkingHoursDTO>> getAll(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId) {
        return ResponseEntity.ok(
                service.getByBusiness(businessId)
        );
    }

    @Operation(summary = "Update a working-hours interval",
            description = "Updates one opening interval. Only the business owner may call this.")
    @ApiResponse(responseCode = "200", description = "Working hours updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body, or the open time is not before the close time")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner, or the entry belongs to another business")
    @ApiResponse(responseCode = "404", description = "Business or working-hours entry not found")
    @ApiResponse(responseCode = "409", description = "The interval overlaps an existing one for that weekday")
    @PutMapping("/{id}")
    public ResponseEntity<BusinessWorkingHoursDTO> update(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Working-hours entry ID", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody CreateWorkingHoursRequest request,
            Authentication authentication) {

        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(
                service.update(businessId, id, request, currentUser)
        );
    }

    @Operation(summary = "Delete a working-hours interval",
            description = "Removes one opening interval, closing the business for that stretch of the weekday")
    @ApiResponse(responseCode = "204", description = "Working hours deleted successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner, or the entry belongs to another business")
    @ApiResponse(responseCode = "404", description = "Business or working-hours entry not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Working-hours entry ID", example = "1")
            @PathVariable Long id,
            Authentication authentication) {

        User currentUser = userService.getUserByUsername(authentication.getName());
        service.delete(businessId, id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
