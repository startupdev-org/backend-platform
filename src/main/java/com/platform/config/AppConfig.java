package com.platform.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration class to access application properties.
 */
@Component
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AppConfig {

    @Value("${spring.application.name}") // Injects 'spring.application.name' property
    private String appName;

    @Value("${spring.profiles.active}")
    private String appProfileActive;


}
