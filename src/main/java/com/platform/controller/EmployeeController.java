package com.platform.controller;

import com.platform.dto.availability.AvailabilityResponseDTO;
import com.platform.dto.employee.EmployeeRequestDTO;
import com.platform.dto.employee.EmployeeResponseDTO;
import com.platform.entity.User;
import com.platform.service.AvailabilityService;
import com.platform.service.EmployeeService;
import com.platform.service.UserService;
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

@RestController
@RequestMapping("/api/business/{businessId}/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AvailabilityService availabilityService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<EmployeeResponseDTO>> listEmployees(
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(employeeService.getBusinessEmployees(businessId,
                PageRequests.of(page, size, sort, EmployeeService.SORTABLE_FIELDS, EmployeeService.DEFAULT_SORT)));
    }

    @GetMapping("/list")
    public ResponseEntity<List<EmployeeResponseDTO>> listEmployees(
            @PathVariable UUID businessId
    ) {
        return ResponseEntity.ok(employeeService.getBusinessEmployeesList(businessId));
    }

    @GetMapping("/{employeeId}")
    public ResponseEntity<EmployeeResponseDTO> getEmployee(@PathVariable UUID employeeId) {
        EmployeeResponseDTO employee = employeeService.getEmployee(employeeId);
        return ResponseEntity.ok(employee);
    }

    // Public: the booking page needs bookable start times without a login. Covered by the
    // GET /api/business/*/employee/** permitAll rule in SecurityConfig.
    @GetMapping("/{employeeId}/availability")
    public ResponseEntity<AvailabilityResponseDTO> getEmployeeAvailability(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            @RequestParam UUID serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(
                availabilityService.getEmployeeAvailability(businessId, employeeId, serviceId, date));
    }

    @GetMapping("/{employeeId}/admin")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<EmployeeResponseDTO> getEmployeeForAdmin(
            @PathVariable UUID employeeId,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(employeeService.getEmployeeForAdmin(employeeId, currentUser));
    }

    @GetMapping("/active")
    public ResponseEntity<Page<EmployeeResponseDTO>> listActiveEmployees(
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(employeeService.getActiveEmployees(businessId,
                PageRequests.of(page, size, sort, EmployeeService.SORTABLE_FIELDS, EmployeeService.DEFAULT_SORT)));
    }

    @PostMapping
    public ResponseEntity<EmployeeResponseDTO> createEmployee(
            @PathVariable("businessId") UUID businessId,
            @Valid @RequestBody EmployeeRequestDTO request) {
        EmployeeResponseDTO employee = employeeService.createEmployee(businessId, request);
        return new ResponseEntity<>(employee, HttpStatus.CREATED);
    }

    @PutMapping("/{employeeId}")
    public ResponseEntity<EmployeeResponseDTO> updateEmployee(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeRequestDTO request,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        EmployeeResponseDTO employee = employeeService.updateEmployee(businessId, employeeId, request, currentUser);
        return ResponseEntity.ok(employee);
    }

    @DeleteMapping("/{employeeId}")
    public ResponseEntity<Void> deleteEmployee(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        employeeService.deleteEmployee(businessId, employeeId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{employeeId}/permanent")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> hardDeleteEmployee(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        employeeService.hardDeleteEmployee(businessId, employeeId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
