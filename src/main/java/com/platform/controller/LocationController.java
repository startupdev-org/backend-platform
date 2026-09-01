package com.platform.controller;

import com.platform.dto.location.LocationRequestDTO;
import com.platform.dto.location.LocationResponseDTO;
import com.platform.entity.User;
import com.platform.security.CurrentUser;
import com.platform.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Reads are public - the booking page shows a business's addresses with no login.
 * Writes are BUSINESS_ADMIN and ownership-checked in {@link LocationService}.
 *
 * <p>bearerAuth is declared per method, not on the class, so the empty
 * {@code @SecurityRequirements} on the public reads is not overridden.
 */
@Tag(name = "Location", description = "Business location management endpoints")
@RestController
@RequestMapping("/api/business/{businessId}/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @Operation(summary = "Create a location",
            description = "Adds a location to the business. Only the owner may call this.")
    @ApiResponse(responseCode = "201", description = "Location created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<LocationResponseDTO> createLocation(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            @Valid @RequestBody LocationRequestDTO requestDTO,
            @CurrentUser User currentUser) {
        LocationResponseDTO responseDTO = locationService.createLocation(businessId, requestDTO, currentUser);
        return new ResponseEntity<>(responseDTO, HttpStatus.CREATED);
    }

    @Operation(summary = "List a business's locations",
            description = "Returns every location of the business. Public - no authentication required.")
    @ApiResponse(responseCode = "200", description = "Locations retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<List<LocationResponseDTO>> getLocationsForBusiness(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId) {
        List<LocationResponseDTO> locations = locationService.getLocationsForBusiness(businessId);
        return ResponseEntity.ok(locations);
    }

    @Operation(summary = "Get a location by ID",
            description = "Returns a single location of the business. Public - no authentication required.")
    @ApiResponse(responseCode = "200", description = "Location found")
    @ApiResponse(responseCode = "404", description = "Business or location not found")
    @SecurityRequirements
    @GetMapping("/{locationId}")
    public ResponseEntity<LocationResponseDTO> getLocationById(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Location UUID")
            @PathVariable UUID locationId) {
        LocationResponseDTO location = locationService.getLocationById(businessId, locationId);
        return ResponseEntity.ok(location);
    }

    @Operation(summary = "Update a location",
            description = "Updates a location of the business. Only the owner may call this.")
    @ApiResponse(responseCode = "200", description = "Location updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner")
    @ApiResponse(responseCode = "404", description = "Business or location not found")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{locationId}")
    public ResponseEntity<LocationResponseDTO> updateLocation(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Location UUID")
            @PathVariable UUID locationId,
            @Valid @RequestBody LocationRequestDTO requestDTO,
            @CurrentUser User currentUser) {
        LocationResponseDTO updatedLocation = locationService.updateLocation(businessId, locationId, requestDTO, currentUser);
        return ResponseEntity.ok(updatedLocation);
    }

    @Operation(summary = "Delete a location",
            description = "Removes a location from the business. Only the owner may call this.")
    @ApiResponse(responseCode = "204", description = "Location deleted successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner")
    @ApiResponse(responseCode = "404", description = "Business or location not found")
    @ApiResponse(responseCode = "409", description = "Location is still referenced by other records")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{locationId}")
    public ResponseEntity<Void> deleteLocation(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Location UUID")
            @PathVariable UUID locationId,
            @CurrentUser User currentUser) {
        locationService.deleteLocation(businessId, locationId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
