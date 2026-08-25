package com.platform.controller;

import com.platform.dto.service.ServiceRequestDTO;
import com.platform.dto.service.ServiceResponseDTO;
import com.platform.entity.User;
import com.platform.service.ProvidedServicesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Service", description = "Business service management endpoints")
@RestController
@RequestMapping("/api/business/{businessId}/service")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ServiceController {

    private final ProvidedServicesService providedServicesService;

    @Operation(summary = "Create a service", description = "Creates a new service for the specified business")
    @ApiResponse(responseCode = "201", description = "Service created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Not authorized to create a service for this business")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @PostMapping
    public ResponseEntity<ServiceResponseDTO> createService(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            @Valid @RequestBody ServiceRequestDTO request) {
        ServiceResponseDTO service = providedServicesService.createService(businessId, request);
        return new ResponseEntity<>(service, HttpStatus.CREATED);
    }

    @Operation(summary = "Get service by ID", description = "Returns a single service by its UUID")
    @ApiResponse(responseCode = "200", description = "Service found")
    @ApiResponse(responseCode = "404", description = "Service not found")
    @GetMapping("/{serviceId}")
    public ResponseEntity<ServiceResponseDTO> getService(
            @Parameter(description = "Service UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID serviceId) {
        ServiceResponseDTO service = providedServicesService.getService(serviceId);
        return ResponseEntity.ok(service);
    }

    @Operation(summary = "List all services", description = "Returns paginated services for the specified business")
    @ApiResponse(responseCode = "200", description = "Services retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @GetMapping
    public ResponseEntity<Page<ServiceResponseDTO>> listServices(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ServiceResponseDTO> services = providedServicesService.getBusinessServices(businessId, PageRequest.of(page, size));
        return ResponseEntity.ok(services);
    }

    @Operation(summary = "List active services", description = "Returns paginated active services for the specified business")
    @ApiResponse(responseCode = "200", description = "Active services retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @GetMapping("/active")
    public ResponseEntity<Page<ServiceResponseDTO>> listActiveServices(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ServiceResponseDTO> services = providedServicesService.getActiveServices(businessId, PageRequest.of(page, size));
        return ResponseEntity.ok(services);
    }

    @Operation(summary = "Update a service", description = "Updates an existing service by ID for the specified business")
    @ApiResponse(responseCode = "200", description = "Service updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Not authorized to update this service")
    @ApiResponse(responseCode = "404", description = "Business or service not found")
    @PutMapping("/{serviceId}")
    public ResponseEntity<ServiceResponseDTO> updateService(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            @Parameter(description = "Service UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID serviceId,
            @Valid @RequestBody ServiceRequestDTO request) {
        ServiceResponseDTO service = providedServicesService.updateService(businessId, serviceId, request);
        return ResponseEntity.ok(service);
    }

    @Operation(summary = "Delete a service", description = "Deletes a service by ID, only the business owner can delete it")
    @ApiResponse(responseCode = "204", description = "Service deleted successfully")
    @ApiResponse(responseCode = "403", description = "Not authorized to delete this service")
    @ApiResponse(responseCode = "404", description = "Business or service not found")
    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            @Parameter(description = "Service UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID serviceId,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        providedServicesService.deleteService(businessId, serviceId, currentUser);
        return ResponseEntity.noContent().build();
    }
}