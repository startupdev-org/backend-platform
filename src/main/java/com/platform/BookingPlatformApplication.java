package com.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// For RefreshTokenCleanupJob. Every instance runs its own timer; the cleanup is an
// idempotent DELETE, so more than one Render instance doing it is harmless.
@EnableScheduling
public class BookingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingPlatformApplication.class, args);
    }

}
