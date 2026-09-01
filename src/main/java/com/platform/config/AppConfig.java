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

    // Defaulted, like application.yml's own SPRING_PROFILES_ACTIVE placeholder. Without
    // it this bean is a second, independent reason a context with no profile set fails
    // to start - and it fails at bean creation, well away from the config that caused it.
    @Value("${spring.profiles.active:dev}")
    private String appProfileActive;


}
