package com.platform.config;

import com.platform.storage.StorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    /**
     * Timeouts are not optional here. Without them a hung storage provider holds a
     * request thread open indefinitely, which on a single small instance is enough to
     * take the whole API down - the RestTemplate this replaces had no timeouts at all.
     */
    @Bean
    public RestTemplate storageRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
