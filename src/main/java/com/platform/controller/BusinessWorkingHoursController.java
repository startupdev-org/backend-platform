package com.platform.controller;

import com.platform.dto.business.CreateWorkingHoursRequest;
import com.platform.dto.business.BusinessWorkingHoursDTO;
import com.platform.entity.User;
import com.platform.service.BusinessWorkingHoursService;
import com.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/business/{businessId}/working-hours")
@RequiredArgsConstructor
public class BusinessWorkingHoursController {

    private final BusinessWorkingHoursService service;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<BusinessWorkingHoursDTO> create(
            @PathVariable UUID businessId,
            @RequestBody CreateWorkingHoursRequest request,
            Authentication authentication) {

        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(
                service.create(businessId, request, currentUser)
        );
    }

    @GetMapping
    public ResponseEntity<List<BusinessWorkingHoursDTO>> getAll(@PathVariable UUID businessId) {
        return ResponseEntity.ok(
                service.getByBusiness(businessId)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessWorkingHoursDTO> update(
            @PathVariable UUID businessId,
            @PathVariable Long id,
            @RequestBody CreateWorkingHoursRequest request,
            Authentication authentication) {

        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(
                service.update(businessId, id, request, currentUser)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable UUID businessId,
            @PathVariable Long id,
            Authentication authentication) {

        User currentUser = userService.getUserByUsername(authentication.getName());
        service.delete(businessId, id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
