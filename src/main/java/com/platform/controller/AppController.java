package com.platform.controller;

import com.platform.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller to expose application configuration.
 */
@RequiredArgsConstructor
@RestController
public class AppController {

    private AppConfig appConfig;

    /**
     * Endpoint to show configuration properties.
     */
    @GetMapping(value = "/config", produces = "text/plain")
    public String showConfig() {
        return "Application Name: " + appConfig.getAppName() +
                "\nApplication Active Profile: " + appConfig.getAppProfileActive();
    }
}
