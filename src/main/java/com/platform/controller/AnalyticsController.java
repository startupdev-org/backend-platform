package com.platform.controller;

import com.platform.dto.analytics.BusinessDashboardDTO;
import com.platform.entity.User;
import com.platform.service.AnalyticsService;
import com.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService userService;

    @GetMapping("/business/{businessId}/dashboard")
    public ResponseEntity<BusinessDashboardDTO> getBusinessDashboard(
            @PathVariable UUID businessId,
            Authentication authentication) {
        User currentUser = userService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(analyticsService.getBusinessDashboard(businessId, currentUser));
    }
}
