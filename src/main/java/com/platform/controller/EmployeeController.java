package com.platform.controller;

import com.platform.dto.availability.AvailabilityResponseDTO;
import com.platform.dto.employee.EmployeeRequestDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.entity.User;
import com.platform.service.AvailabilityService;
import com.platform.service.EmployeeService;
import com.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import com.platform.utils.PageRequests;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads are public - the booking page lists a business's staff and their free slots with
 * no login. Writes are BUSINESS_ADMIN, and the two admin-only routes are PLATFORM_ADMIN.
 *
 * <p>bearerAuth is declared per method rather than on the class: a class-level
 * {@code @SecurityRequirement} wins over a method-level one, which would put a lock in
 * Swagger on the public reads. The empty {@code @SecurityRequirements} on those reads is
 * what clears the global requirement declared in {@code OpenApiConfig}.
 */
@Tag(name = "Employee", description = "Employee management endpoints, scoped to one business")
@RestController
@RequestMapping("/api/business/{businessId}/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AvailabilityService availabilityService;
    private final UserService userService;

    @Operation(summary = "List employees",
            description = "Returns a paginated list of the business's employees. Public - no authentication required.")
    @ApiResponse(responseCode = "200", description = "Employees retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameter")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @SecurityRequirements
    @GetMapping
    public ResponseEntity<Page<EmployeeResponseDTO>> listEmployees(
            @Parameter(description = "Business UUID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID businessId,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as 'field' or 'field,asc|desc'", example = "name,asc")
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(employeeService.getBusinessEmployees(businessId,
                PageRequests.of(page, size, sort, EmployeeService.SORTABLE_FIELDS, EmployeeService.DEFAULT_SORT)));
    }

    @Operation(summary = "List employees, unpaginated",
            description = "Returns every employee of the business as a flat list. Public - no authentication required.")
    @ApiResponse(responseCode = "200", description = "Employees retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @SecurityRequirements
    @GetMapping("/list")
    public ResponseEntity<List<EmployeeResponseDTO>> listEmployees(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId
    ) {
        return ResponseEntity.ok(employeeService.getBusinessEmployeesList(businessId));
    }

    @Operation(summary = "Get employee by ID",
            description = "Returns a single active employee. Public - no authentication required.")
    @ApiResponse(responseCode = "200", description = "Employee found")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    @SecurityRequirements
    @GetMapping("/{employeeId}")
    public ResponseEntity<EmployeeResponseDTO> getEmployee(
            @Parameter(description = "Employee UUID")
            @PathVariable UUID employeeId) {
        EmployeeResponseDTO employee = employeeService.getEmployee(employeeId);
        return ResponseEntity.ok(employee);
    }

    // Public: the booking page needs bookable start times without a login. Covered by the
    // GET /api/business/*/employee/** permitAll rule in SecurityConfig.
    @Operation(summary = "Get employee availability",
            description = "Returns the bookable start times for one employee, service and date, "
                    + "derived from the business's working hours minus existing bookings. "
                    + "A weekday with no working-hours row returns an empty list. "
                    + "Public - the booking page needs this without a login.")
    @ApiResponse(responseCode = "200", description = "Availability computed successfully")
    @ApiResponse(responseCode = "400", description = "Malformed date or identifier")
    @ApiResponse(responseCode = "404", description = "Business, employee or service not found")
    @SecurityRequirements
    @GetMapping("/{employeeId}/availability")
    public ResponseEntity<AvailabilityResponseDTO> getEmployeeAvailability(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Employee UUID")
            @PathVariable UUID employeeId,
            @Parameter(description = "Service to book, which fixes the slot duration")
            @RequestParam UUID serviceId,
            @Parameter(description = "Day to check, as ISO-8601", example = "2026-09-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(
                availabilityService.getEmployeeAvailability(businessId, employeeId, serviceId, date));
    }

    @Operation(summary = "Get employee by ID, including disabled",
            description = "Platform-admin lookup that also returns soft-deleted employees")
    @ApiResponse(responseCode = "200", description = "Employee found")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not a platform administrator")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{employeeId}/admin")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<EmployeeResponseDTO> getEmployeeForAdmin(
            @Parameter(description = "Employee UUID")
            @PathVariable UUID employeeId,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(employeeService.getEmployeeForAdmin(employeeId, currentUser));
    }

    @Operation(summary = "List active employees",
            description = "Returns a paginated list of the business's active employees. Public - no authentication required.")
    @ApiResponse(responseCode = "200", description = "Employees retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Invalid pagination or sort parameter")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @SecurityRequirements
    @GetMapping("/active")
    public ResponseEntity<Page<EmployeeResponseDTO>> listActiveEmployees(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort as 'field' or 'field,asc|desc'", example = "name,asc")
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(employeeService.getActiveEmployees(businessId,
                PageRequests.of(page, size, sort, EmployeeService.SORTABLE_FIELDS, EmployeeService.DEFAULT_SORT)));
    }

    @Operation(summary = "Create an employee",
            description = "Adds an employee to the business. Photos are not settable here - use the image endpoints.")
    @ApiResponse(responseCode = "201", description = "Employee created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not authorized to add employees to this business")
    @ApiResponse(responseCode = "404", description = "Business not found")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<EmployeeResponseDTO> createEmployee(
            @Parameter(description = "Business UUID")
            @PathVariable("businessId") UUID businessId,
            @Valid @RequestBody EmployeeRequestDTO request) {
        EmployeeResponseDTO employee = employeeService.createEmployee(businessId, request);
        return new ResponseEntity<>(employee, HttpStatus.CREATED);
    }

    @Operation(summary = "Update an employee",
            description = "Updates an employee of the business. Only the business owner may call this.")
    @ApiResponse(responseCode = "200", description = "Employee updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request body")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not authorized to update this employee")
    @ApiResponse(responseCode = "404", description = "Business or employee not found")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{employeeId}")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Employee UUID")
            @PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeRequestDTO request,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        EmployeeResponseDTO employee = employeeService.updateEmployee(businessId, employeeId, request, currentUser);
        return ResponseEntity.ok(employee);
    }

    @Operation(summary = "Deactivate an employee",
            description = "Soft-deletes an employee, keeping their booking history intact")
    @ApiResponse(responseCode = "204", description = "Employee deactivated successfully")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not authorized to delete this employee")
    @ApiResponse(responseCode = "404", description = "Business or employee not found")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{employeeId}")
    public ResponseEntity<Void> deleteEmployee(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Employee UUID")
            @PathVariable UUID employeeId,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        employeeService.deleteEmployee(businessId, employeeId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete an employee permanently",
            description = "Removes the employee row outright. Platform-admin only, and irreversible.")
    @ApiResponse(responseCode = "204", description = "Employee deleted permanently")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not a platform administrator")
    @ApiResponse(responseCode = "404", description = "Business or employee not found")
    @ApiResponse(responseCode = "409", description = "Employee is still referenced by other records")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{employeeId}/permanent")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> hardDeleteEmployee(
            @Parameter(description = "Business UUID")
            @PathVariable UUID businessId,
            @Parameter(description = "Employee UUID")
            @PathVariable UUID employeeId,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        employeeService.hardDeleteEmployee(businessId, employeeId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
