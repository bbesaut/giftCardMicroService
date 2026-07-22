package com.finovago.p2p.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimitFilter Tests")
class RateLimitFilterTest {

    private static final int CAPACITY = 3;
    private static final String PROTECTED_URI = "/api/v1/giftcards/lookup/GC-12345";
    private static final String UNPROTECTED_URI = "/api/v1/giftcards/list";

    private RateLimitFilter filter;
    private FilterChain filterChain;
    private AtomicInteger chainInvocations;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(true, CAPACITY, 60);
        chainInvocations = new AtomicInteger();
        filterChain = (req, res) -> chainInvocations.incrementAndGet();
    }

    private MockHttpServletRequest requestFor(String uri, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    @DisplayName("Should allow requests within capacity")
    void allowsRequestsWithinCapacity() throws ServletException, IOException {
        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.1"), response, filterChain);
            assertEquals(200, response.getStatus(), "Filter must not touch the response status when the request is allowed through");
        }
        assertEquals(CAPACITY, chainInvocations.get());
    }

    @Test
    @DisplayName("Should return 429 once capacity is exceeded for the same IP")
    void blocksRequestsBeyondCapacity() throws ServletException, IOException {
        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.1"), new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.1"), blockedResponse, filterChain);

        assertEquals(429, blockedResponse.getStatus());
        assertEquals(CAPACITY, chainInvocations.get(), "Chain must not be invoked once the bucket is exhausted");
        assertNotNull(blockedResponse.getHeader("Retry-After"));
    }

    @Test
    @DisplayName("429 response body should not contain a correlation id/code field")
    void blockedResponseBodyHasNoCodeField() throws ServletException, IOException {
        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.2"), new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.2"), blockedResponse, filterChain);

        JsonNode body = new ObjectMapper().readTree(blockedResponse.getContentAsString());
        assertEquals("Too Many Requests", body.get("error").asText());
        assertFalse(body.has("code"), "Error body should not duplicate the correlation id (already in X-Correlation-Id header)");
    }

    @Test
    @DisplayName("Should track separate quotas per client IP")
    void tracksSeparateQuotasPerIp() throws ServletException, IOException {
        for (int i = 0; i < CAPACITY; i++) {
            filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.3"), new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.4"), otherIpResponse, filterChain);

        assertEquals(200, otherIpResponse.getStatus(), "A different IP must have its own, unexhausted quota");
        assertEquals(CAPACITY + 1, chainInvocations.get());
    }

    @Test
    @DisplayName("Should not filter requests to unprotected endpoints")
    void doesNotFilterUnprotectedPaths() {
        assertTrue(filter.shouldNotFilter(requestFor(UNPROTECTED_URI, "10.0.0.5")));
        assertFalse(filter.shouldNotFilter(requestFor(PROTECTED_URI, "10.0.0.5")));
    }

    @Test
    @DisplayName("Should bypass rate limiting entirely when disabled")
    void bypassesWhenDisabled() throws ServletException, IOException {
        RateLimitFilter disabledFilter = new RateLimitFilter(false, CAPACITY, 60);

        // Go through doFilter() (not doFilterInternal directly) so shouldNotFilter() is actually consulted,
        // exactly like the real servlet container would.
        for (int i = 0; i < CAPACITY + 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            disabledFilter.doFilter(requestFor(PROTECTED_URI, "10.0.0.6"), response, filterChain);
            assertEquals(200, response.getStatus());
        }
        assertEquals(CAPACITY + 5, chainInvocations.get());
    }
}
