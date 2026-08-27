package com.platform.controller;

import com.platform.dto.EmployeeLocationServicePriceRequestDTO;
import com.platform.dto.EmployeeLocationServicePriceResponseDTO;
import com.platform.entity.User;
import com.platform.service.EmployeeLocationServicePriceService;
import com.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Per-employee, per-location pricing: the three-way join of employee, service and location.
 *
 * <p>Writes are BUSINESS_ADMIN and ownership-checked in the service. The reads are currently
 * only {@code .authenticated()} in SecurityConfig with no ownership check in the service, so
 * one tenant can read another's price table - a known gap to close before billing lands, and
 * the reason the read operations below promise no tenant scoping.
 */
@Tag(name = "Employee pricing", description = "Per-employee, per-location service pricing")
@RestController
@RequestMapping("/api/business/{businessId}/employee-service-price")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class EmployeeLocationServicePriceController {

    private final EmployeeLocationServicePriceService priceService;
    private final UserService userService;

    @Operation(summary = "Create a price entry",
            description = "Sets the price for one employee, service and location combination")
    @ApiResponse(responseCode = "201", description = "Price entry created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner, the referenced records "
            + "belong to another business, or a price entry for this combination already exists")
    @ApiResponse(responseCode = "404", description = "Business, employee, service or location not found")
    @PostMapping
    public ResponseEntity<EmployeeLocationServicePriceResponseDTO> create(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            @Valid @RequestBody EmployeeLocationServicePriceRequestDTO dto,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(priceService.create(businessId, dto, currentUser));
    }

    @Operation(summary = "Update a price entry",
            description = "Updates an existing employee/service/location price")
    @ApiResponse(responseCode = "200", description = "Price entry updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner, the referenced records "
            + "belong to another business, or the new combination is already taken")
    @ApiResponse(responseCode = "404", description = "Price entry, business, employee, service or location not found")
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeLocationServicePriceResponseDTO> update(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Price entry UUID")
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeLocationServicePriceRequestDTO dto,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(priceService.update(businessId, id, dto, currentUser));
    }

    @Operation(summary = "Get a price entry by ID",
            description = "Returns a single price entry belonging to the business in the path")
    @ApiResponse(responseCode = "200", description = "Price entry found")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Business or price entry not found")
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeLocationServicePriceResponseDTO> getById(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Price entry UUID")
            @PathVariable UUID id) {
        return ResponseEntity.ok(priceService.getById(businessId, id));
    }

    @Operation(summary = "List an employee's prices",
            description = "Returns every price entry for one employee, across all locations")
    @ApiResponse(responseCode = "200", description = "Price entries retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Business or employee not found")
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<EmployeeLocationServicePriceResponseDTO>> getByEmployee(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Employee UUID")
            @PathVariable UUID employeeId) {
        return ResponseEntity.ok(priceService.getByEmployee(businessId, employeeId));
    }

    @Operation(summary = "List an employee's prices at one location",
            description = "Returns the price entries for one employee at one location - what the "
                    + "booking page needs to show the price of each service")
    @ApiResponse(responseCode = "200", description = "Price entries retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Business, employee or location not found")
    @GetMapping("/employee/{employeeId}/location/{locationId}")
    public ResponseEntity<List<EmployeeLocationServicePriceResponseDTO>> getByEmployeeAndLocation(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Employee UUID")
            @PathVariable UUID employeeId,
            @Parameter(description = "Location UUID")
            @PathVariable UUID locationId) {
        return ResponseEntity.ok(priceService.getByEmployeeAndLocation(businessId, employeeId, locationId));
    }

    @Operation(summary = "Delete a price entry",
            description = "Removes one employee/service/location price")
    @ApiResponse(responseCode = "204", description = "Price entry deleted successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not the business owner")
    @ApiResponse(responseCode = "404", description = "Business or price entry not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Price entry UUID")
            @PathVariable UUID id,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        priceService.delete(businessId, id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
