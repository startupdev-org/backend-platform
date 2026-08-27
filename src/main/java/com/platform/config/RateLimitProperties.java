package com.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for the two abuse controls on the public auth endpoints:
 * a per-IP request throttle (see {@code RateLimitFilter}) and a per-account
 * lockout after repeated failed logins (see {@code AuthService}).
 *
 * <p>The throttle is in-memory and therefore per-instance. It is sized for a
 * single Render instance; a scale-out would need a shared store.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** Master switch for the per-IP request throttle. */
    private boolean enabled = true;

    /** Burst size of the per-IP token bucket. */
    private int capacity = 10;

    /** Tokens added back each {@link #refillPeriodSeconds}. */
    private int refillTokens = 10;

    /** Refill window, seconds. */
    private long refillPeriodSeconds = 60;

    /** Idle time after which an IP's bucket is dropped from the cache. */
    private long cacheExpireMinutes = 15;

    /** Hard ceiling on tracked IPs, to bound memory under a distributed flood. */
    private long cacheMaxSize = 100_000;

    private final Lockout lockout = new Lockout();

    @Getter
    @Setter
    public static class Lockout {

        /** Master switch for per-account lockout. */
        private boolean enabled = true;

        /** Consecutive failed logins before the account is locked. */
        private int maxFailedAttempts = 5;

        /** Lock length at the first lockout; doubles on each further failure. */
        private long baseMinutes = 15;

        /** Upper bound on the (doubling) lock length. */
        private long maxMinutes = 1440;
    }
}
