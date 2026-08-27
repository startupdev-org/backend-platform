package com.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.platform.config.RateLimitProperties;
import com.platform.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private ObjectMapper objectMapper;
    private RateLimitProperties properties;
    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        properties = new RateLimitProperties();
        properties.setCapacity(2);
        properties.setRefillTokens(2);
        properties.setRefillPeriodSeconds(60);
        filter = new RateLimitFilter(properties, objectMapper);
        chain = mock(FilterChain.class);
    }

    private MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void passesThroughNonAuthPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/business/123");
        request.setRemoteAddr("203.0.113.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void allowsRequestsWithinCapacity() throws Exception {
        for (int i = 0; i < 2; i++) {
            filter.doFilter(loginRequest("203.0.113.2"), new MockHttpServletResponse(), chain);
        }
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksWithJson429WhenCapacityExceeded() throws Exception {
        filter.doFilter(loginRequest("203.0.113.3"), new MockHttpServletResponse(), chain);
        filter.doFilter(loginRequest("203.0.113.3"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginRequest("203.0.113.3"), blocked, chain);

        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getContentType());
        org.junit.jupiter.api.Assertions.assertTrue(blocked.getContentType().startsWith("application/json"));
        assertNotNull(blocked.getHeader("Retry-After"));

        ErrorResponse body = objectMapper.readValue(blocked.getContentAsString(), ErrorResponse.class);
        assertEquals(429, body.getStatus());
        assertEquals("/api/auth/login", body.getPath());
    }

    @Test
    void bucketsArePerIp() throws Exception {
        filter.doFilter(loginRequest("203.0.113.4"), new MockHttpServletResponse(), chain);
        filter.doFilter(loginRequest("203.0.113.4"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse otherIp = new MockHttpServletResponse();
        filter.doFilter(loginRequest("203.0.113.5"), otherIp, chain);

        assertEquals(200, otherIp.getStatus());
        verify(chain, times(3)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void disabledFilterPassesEverythingThrough() throws Exception {
        properties.setEnabled(false);
        RateLimitFilter disabled = new RateLimitFilter(properties, objectMapper);

        for (int i = 0; i < 5; i++) {
            disabled.doFilter(loginRequest("203.0.113.6"), new MockHttpServletResponse(), chain);
        }
        verify(chain, times(5)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(chain, never()).doFilter(null, null);
    }
}
