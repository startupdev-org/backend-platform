package com.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Legacy alias, kept only so any existing caller of {@code GET /api/health} keeps
 * working. It is intentionally just a liveness-of-the-HTTP-stack ping - it does not
 * touch the datasource or anything else - so it must never be treated as a real
 * health check.
 *
 * <p>The real probes are Actuator's (BP-66): {@code /actuator/health/liveness} and
 * {@code /actuator/health/readiness}, the latter backed by the DataSource health
 * indicator so a database outage actually shows. {@code GET /api/health/check} - the
 * old hardcoded "Everything is checked" endpoint that could never fail - is removed
 * rather than kept, since it was actively misleading.
 */
@Tag(name = "Health", description = "Legacy health alias - see /actuator/health/liveness and /actuator/health/readiness")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    @Operation(summary = "Legacy health alias", description = "Confirms the HTTP stack is up. Not a real health check - see /actuator/health/liveness and /actuator/health/readiness")
    @ApiResponse(responseCode = "200", description = "Service is up and running")
    @GetMapping
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("The web service is up and running");
    }
}