package com.platform.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    void usesFirstEntryOfXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.5, 10.0.0.1, 10.0.0.2");

        assertEquals("198.51.100.5", ClientIpResolver.resolve(request));
    }

    @Test
    void fallsBackToRemoteAddrWhenHeaderAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.44");

        assertEquals("192.0.2.44", ClientIpResolver.resolve(request));
    }

    @Test
    void fallsBackToRemoteAddrWhenHeaderBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("192.0.2.44");

        assertEquals("192.0.2.44", ClientIpResolver.resolve(request));
    }
}
