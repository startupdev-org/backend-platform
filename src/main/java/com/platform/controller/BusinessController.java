package com.platform.controller;

import com.platform.dto.business.BusinessRequestDTO;
import com.platform.dto.business.BusinessResponseDTO;
import com.platform.entity.User;
import com.platform.service.BusinessService;
import com.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import com.platform.utils.PageRequests;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Business", description = "Business management endpoints")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BusinessController {

    private final BusinessService businessService;
    private final UserService userService;

    @Operation(summary = "List businesses", description = "Returns a paginated list of businesses, optionally filtered by city and minimum rating")
    @ApiResponse(responseCode = "200", description = "Businesses retrieved successfully")
    @GetMapping
    public ResponseEntity<Page<BusinessResponseDTO>> listBusinesses(
            @Parameter(description = "Filter by city name", example = "Chisinau")
            @RequestParam(required = false) String city,
            @Parameter(description = "Filter by minimum rating", example = "4.0")
            @RequestParam(required = false) Double minRating,
            @Parameter(description = "Filter by category", example = "BARBERSHOP")
            @RequestParam(required = false) String businessCategory,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as 'field' or 'field,asc|desc'", example = "name,asc")
            @RequestParam(required = false) String sort) {
        Page<BusinessResponseDTO> businesses = businessService.listBusinesses(city, minRating, businessCategory,
                PageRequests.of(page, size, sort, BusinessService.SORTABLE_FIELDS, BusinessService.DEFAULT_SORT));
        return ResponseEntity.ok(businesses);
    }

    @Operation(summary = "List businesses by query", description = "Returns a paginated list of businesses, filtered by query")
    @ApiResponse(responseCode = "200", description = "Businesses retrieved successfully")
    @GetMapping("/query")
    public ResponseEntity<Page<BusinessResponseDTO>> listBusinesses(
            @RequestParam(name = "query") String query,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as 'field' or 'field,asc|desc'", example = "name,asc")
            @RequestParam(required = false) String sort) {
        Page<BusinessResponseDTO> businesses = businessService.listBusinessesByQuery(query,
                PageRequests.of(page, size, sort, BusinessService.SORTABLE_FIELDS, BusinessService.DEFAULT_SORT));
        return ResponseEntity.ok(businesses);
    }

    @Operation(summary = "Get business by slug", description = "Returns a single business by its unique slug")
    @ApiResponse(responseCode = "200", description = "Business found")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<BusinessResponseDTO> getBySlug(
            @Parameter(description = "Business slug", example = "my-barbershop")
            @PathVariable String slug) {
        return ResponseEntity.ok(businessService.getBusinessBySlug(slug));
    }

    @Operation(summary = "Get business by ID", description = "Returns a single business by its UUID")
    @ApiResponse(responseCode = "200", description = "Business found")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @GetMapping("/{id}")
    public ResponseEntity<BusinessResponseDTO> getById(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id) {
        BusinessResponseDTO business = businessService.getBusinessDTOById(id);
        return ResponseEntity.ok(business);
    }

    @Operation(summary = "Create a business", description = "Creates a new business for the authenticated user")
    @ApiResponse(responseCode = "201", description = "Business created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Not authorized to create a business")
    @PostMapping
    public ResponseEntity<BusinessResponseDTO> createBusiness(
            @Valid @RequestBody BusinessRequestDTO request) {
        BusinessResponseDTO business = businessService.createBusiness(request);
        return new ResponseEntity<>(business, HttpStatus.CREATED);
    }

    @Operation(summary = "Update a business", description = "Updates an existing business by ID")
    @ApiResponse(responseCode = "200", description = "Business updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "403", description = "Not authorized to update this business")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @PutMapping("/{id}")
    public ResponseEntity<BusinessResponseDTO> updateBusiness(
            @Parameter(description = "Business UUID")
            @PathVariable UUID id,
            @Valid @RequestBody BusinessRequestDTO request) {
        BusinessResponseDTO business = businessService.updateBusiness(id, request);
        return ResponseEntity.ok(business);
    }

    @Operation(summary = "Delete a business", description = "Deletes a business by ID, only the owner can delete it")
    @ApiResponse(responseCode = "204", description = "Business deleted successfully")
    @ApiResponse(responseCode = "403", description = "Not authorized to delete this business")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBusiness(
            @Parameter(description = "Business UUID")
            @PathVariable UUID id,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        businessService.deleteBusiness(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get my businesses", description = "Returns all businesses owned by the currently authenticated user")
    @ApiResponse(responseCode = "200", description = "Businesses retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Not authenticated")
    @GetMapping("/user/my-businesses")
    public ResponseEntity<List<BusinessResponseDTO>> getUserBusinesses(Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        List<BusinessResponseDTO> businesses = businessService.getUserBusinesses(currentUser.getId());
        return ResponseEntity.ok(businesses);
    }
}