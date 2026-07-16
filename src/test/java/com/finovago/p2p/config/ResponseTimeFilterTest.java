package com.finovago.p2p.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResponseTimeFilter Tests")
class ResponseTimeFilterTest {

    private ResponseTimeFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new ResponseTimeFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Should add X-Response-Time header to response")
    void testResponseTimeHeaderIsAdded() throws IOException, ServletException {
        FilterChain filterChain = (req, res) -> res.getOutputStream().write("{}".getBytes());

        filter.doFilterInternal(request, response, filterChain);

        String headerValue = response.getHeader("X-Response-Time");
        assertNotNull(headerValue);
        assertTrue(headerValue.matches("\\d+"), "Header value should be a number (milliseconds)");

        long duration = Long.parseLong(headerValue);
        assertTrue(duration >= 0, "Duration should be non-negative");
    }

    @Test
    @DisplayName("Should still set the header after the response body has already been written")
    void testResponseTimeHeaderSetAfterBodyWritten() throws IOException, ServletException {
        FilterChain filterChain = (req, res) -> {
            res.getOutputStream().write("{\"accessToken\":\"abc\"}".getBytes());
            res.getOutputStream().flush();
        };

        filter.doFilterInternal(request, response, filterChain);

        String headerValue = response.getHeader("X-Response-Time");
        assertNotNull(headerValue, "X-Response-Time must be set even if the body was flushed inside the chain");
        assertTrue(headerValue.matches("\\d+"), "Duration should be numeric milliseconds");
        assertEquals("{\"accessToken\":\"abc\"}", response.getContentAsString());
    }
}
