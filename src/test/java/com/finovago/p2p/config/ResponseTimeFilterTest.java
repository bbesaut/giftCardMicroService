package com.finovago.p2p.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

@DisplayName("ResponseTimeFilter Tests")
class ResponseTimeFilterTest {

    private ResponseTimeFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new ResponseTimeFilter();
    }

    @Test
    @DisplayName("Should add X-Response-Time header to response")
    void testResponseTimeHeaderIsAdded() throws IOException, ServletException {
        ArgumentCaptor<String> headerNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(headerNameCaptor.capture(), headerValueCaptor.capture());

        assertEquals("X-Response-Time", headerNameCaptor.getValue());

        String headerValue = headerValueCaptor.getValue();
        assertNotNull(headerValue);
        assertTrue(headerValue.matches("\\d+"), "Header value should be a number (milliseconds)");

        long duration = Long.parseLong(headerValue);
        assertTrue(duration >= 0, "Duration should be non-negative");
    }

    @Test
    @DisplayName("Should execute filter chain even if exception occurs")
    void testFilterChainExecutionOnException() throws IOException, ServletException {
        doThrow(new RuntimeException("Test exception")).when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () -> filter.doFilterInternal(request, response, filterChain));

        verify(response).setHeader(eq("X-Response-Time"), anyString());
    }

    @Test
    @DisplayName("Should measure response time accurately")
    void testResponseTimeMeasurement() throws IOException, ServletException {
        doAnswer(invocation -> {
            Thread.sleep(50);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq("X-Response-Time"), headerValueCaptor.capture());

        long duration = Long.parseLong(headerValueCaptor.getValue());
        assertTrue(duration >= 50, "Duration should be at least 50ms (plus overhead)");
    }
}
