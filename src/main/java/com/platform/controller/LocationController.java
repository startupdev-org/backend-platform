package com.platform.controller;

import com.platform.dto.location.LocationRequestDTO;
import com.platform.dto.location.LocationResponseDTO;
import com.platform.entity.User;
import com.platform.service.LocationService;
import com.platform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/business/{businessId}/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final UserService userService;

    // Create a new location
    @PostMapping
    public ResponseEntity<LocationResponseDTO> createLocation(
            @PathVariable UUID businessId,
            @Valid @RequestBody LocationRequestDTO requestDTO,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        LocationResponseDTO responseDTO = locationService.createLocation(businessId, requestDTO, currentUser);
        return new ResponseEntity<>(responseDTO, HttpStatus.CREATED);
    }

    // Get all locations for a business
    @GetMapping
    public ResponseEntity<List<LocationResponseDTO>> getLocationsForBusiness(@PathVariable UUID businessId) {
        List<LocationResponseDTO> locations = locationService.getLocationsForBusiness(businessId);
        return ResponseEntity.ok(locations);
    }

    // Get a location by ID
    @GetMapping("/{locationId}")
    public ResponseEntity<LocationResponseDTO> getLocationById(
            @PathVariable UUID businessId,
            @PathVariable UUID locationId) {
        LocationResponseDTO location = locationService.getLocationById(businessId, locationId);
        return ResponseEntity.ok(location);
    }

    // Update a location
    @PutMapping("/{locationId}")
    public ResponseEntity<LocationResponseDTO> updateLocation(
            @PathVariable UUID businessId,
            @PathVariable UUID locationId,
            @Valid @RequestBody LocationRequestDTO requestDTO,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        LocationResponseDTO updatedLocation = locationService.updateLocation(businessId, locationId, requestDTO, currentUser);
        return ResponseEntity.ok(updatedLocation);
    }

    // Delete a location
    @DeleteMapping("/{locationId}")
    public ResponseEntity<Void> deleteLocation(
            @PathVariable UUID businessId,
            @PathVariable UUID locationId,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        locationService.deleteLocation(businessId, locationId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
