package com.platform.controller;

import com.platform.dto.EmployeeLocationServicePriceRequestDTO;
import com.platform.dto.EmployeeLocationServicePriceResponseDTO;
import com.platform.entity.User;
import com.platform.service.EmployeeLocationServicePriceService;
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
@RequestMapping("/api/business/{businessId}/employee-service-price")
@RequiredArgsConstructor
public class EmployeeLocationServicePriceController {

    private final EmployeeLocationServicePriceService priceService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<EmployeeLocationServicePriceResponseDTO> create(
            @PathVariable UUID businessId,
            @Valid @RequestBody EmployeeLocationServicePriceRequestDTO dto,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(priceService.create(businessId, dto, currentUser));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeLocationServicePriceResponseDTO> update(
            @PathVariable UUID businessId,
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeLocationServicePriceRequestDTO dto,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(priceService.update(businessId, id, dto, currentUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeLocationServicePriceResponseDTO> getById(
            @PathVariable UUID businessId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(priceService.getById(businessId, id));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<EmployeeLocationServicePriceResponseDTO>> getByEmployee(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId) {
        return ResponseEntity.ok(priceService.getByEmployee(businessId, employeeId));
    }

    @GetMapping("/employee/{employeeId}/location/{locationId}")
    public ResponseEntity<List<EmployeeLocationServicePriceResponseDTO>> getByEmployeeAndLocation(
            @PathVariable UUID businessId,
            @PathVariable UUID employeeId,
            @PathVariable UUID locationId) {
        return ResponseEntity.ok(priceService.getByEmployeeAndLocation(businessId, employeeId, locationId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID businessId,
            @PathVariable UUID id,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        priceService.delete(businessId, id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
