package com.platform.controller;

import com.platform.dto.business.BusinessFeatureDTO;
import com.platform.entity.BusinessFeature;
import com.platform.repository.BusinessFeatureRepository;
import com.platform.repository.BusinessRepository;
import com.platform.service.FeatureService;
import jakarta.validation.Valid;
import com.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/business/{businessId}/features")
@RequiredArgsConstructor
public class BusinessFeatureController {

    private final BusinessRepository businessRepository;
    private final BusinessFeatureRepository featureRepository;


    private final UserService userService;

    private final FeatureService featureService;

    // GET all features for a business
    @GetMapping
    public ResponseEntity<Set<BusinessFeatureDTO>> getAll(@PathVariable UUID businessId) {
        return ResponseEntity.ok(featureService.getAllFeatures(businessId));
    }

    // POST - add a feature
    @PostMapping
    public ResponseEntity<BusinessFeatureDTO> addFeature(
            @PathVariable UUID businessId,
            @Valid @RequestBody BusinessFeatureDTO request) {
        return ResponseEntity.ok(featureService.addFeature(request));
    }

    // DELETE - remove a feature
    @DeleteMapping("/{featureId}")
    public ResponseEntity<Void> removeFeature(
            @PathVariable UUID businessId,
            @PathVariable Long featureId) {
        featureService.removeFeature(businessId, featureId);
        return ResponseEntity.noContent().build();
    }
}
