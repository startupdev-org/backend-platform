package com.platform.controller;

import com.platform.config.AppConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller to expose application configuration.
 */
@Tag(name = "App", description = "Application configuration endpoint")
@RequiredArgsConstructor
@RestController
public class AppController {

    private final AppConfig appConfig;

    /**
     * Endpoint to show configuration properties.
     */
    @Operation(summary = "Show configuration",
            description = "Returns the application name and active profile as plain text. "
                    + "Public - no authentication required.")
    @ApiResponse(responseCode = "200", description = "Configuration returned successfully")
    @SecurityRequirements
    @GetMapping(value = "/config", produces = "text/plain")
    public String showConfig() {
        return "Application Name: " + appConfig.getAppName() +
                "\nApplication Active Profile: " + appConfig.getAppProfileActive();
    }
}
