package com.platform.controller;

import com.platform.dto.business.BusinessFeatureDTO;
import com.platform.service.FeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/business/{businessId}/features")
@RequiredArgsConstructor
public class BusinessFeatureController {

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
            @RequestBody BusinessFeatureDTO request) {
        return ResponseEntity.ok(featureService.addFeature(businessId, request));
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
