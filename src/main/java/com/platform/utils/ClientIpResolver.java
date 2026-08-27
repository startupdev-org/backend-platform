package com.platform.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * Resolves the originating client IP for a request.
 *
 * <p>The app runs behind Render's proxy, which always sets {@code X-Forwarded-For}
 * as {@code client, proxy1, proxy2, ...} - the first entry is the real client.
 * Falls back to the socket address when the header is absent (direct calls, tests).
 */
public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",", 2)[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
