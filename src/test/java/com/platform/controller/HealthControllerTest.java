package com.platform.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HealthController is now just the legacy alias described in its javadoc (BP-66) -
 * the real probes live under Actuator (/actuator/health/liveness,
 * /actuator/health/readiness) and need a running Spring context to verify honestly,
 * which this Mockito-only test suite does not stand up. That leaves exactly one
 * behavior worth pinning here: the alias still answers 200 with its fixed message,
 * and /check (the endpoint that could never fail) is gone.
 */
class HealthControllerTest {

    private final HealthController healthController = new HealthController();

    @Test
    void health_returnsOkWithStatusMessage() {
        ResponseEntity<String> response = healthController.health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("The web service is up and running", response.getBody());
    }
}
