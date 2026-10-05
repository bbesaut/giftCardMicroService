package com.finovago.p2p.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("OriginCheckFilter Tests")
class OriginCheckFilterTest {

    private static final String ALLOWED_ORIGIN = "https://app.finovago.com";
    private static final String FOREIGN_ORIGIN = "https://evil.example.com";
    private static final String REFRESH_URI = "/api/v1/auth/refresh";
    private static final String LOGOUT_URI = "/api/v1/auth/logout";
    private static final String UNPROTECTED_URI = "/api/v1/auth/login";

    private OriginCheckFilter originCheckFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        originCheckFilter = new OriginCheckFilter(
                List.of(ALLOWED_ORIGIN, " https://other.finovago.com "),
                JsonMapper.builder().build());
        filterChain = mock(FilterChain.class);
    }

    private MockHttpServletRequest requestFor(String uri, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }

    private MockHttpServletResponse send(MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        originCheckFilter.doFilter(request, response, filterChain);
        return response;
    }

    @Test
    @DisplayName("Should let refresh through when Origin is in the allowlist")
    void allowsRefreshFromAllowedOrigin() throws ServletException, IOException {
        MockHttpServletResponse response = send(requestFor(REFRESH_URI, ALLOWED_ORIGIN));

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should let logout through when Origin is in the allowlist")
    void allowsLogoutFromAllowedOrigin() throws ServletException, IOException {
        MockHttpServletResponse response = send(requestFor(LOGOUT_URI, ALLOWED_ORIGIN));

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should match an allowed origin whose configured value had surrounding whitespace")
    void matchesOriginWithTrimmedConfigValue() throws ServletException, IOException {
        MockHttpServletResponse response = send(requestFor(REFRESH_URI, "https://other.finovago.com"));

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should let refresh through when no Origin header is sent (non-browser caller)")
    void allowsRequestWithoutOrigin() throws ServletException, IOException {
        MockHttpServletResponse response = send(requestFor(REFRESH_URI, null));

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should return 403 on refresh when Origin is not in the allowlist")
    void rejectsRefreshFromForeignOrigin() throws ServletException, IOException {
        MockHttpServletResponse response = send(requestFor(REFRESH_URI, FOREIGN_ORIGIN));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"error\":\"Forbidden\""));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should return 403 on logout when Origin is not in the allowlist")
    void rejectsLogoutFromForeignOrigin() throws ServletException, IOException {
        MockHttpServletResponse response = send(requestFor(LOGOUT_URI, FOREIGN_ORIGIN));

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should reject the literal 'null' origin sent by sandboxed or file:// pages")
    void rejectsNullLiteralOrigin() throws ServletException, IOException {
        MockHttpServletResponse response = send(requestFor(REFRESH_URI, "null"));

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should reject an origin that differs from an allowed one only by scheme")
    void rejectsOriginWithDifferentScheme() throws ServletException, IOException {
        MockHttpServletResponse response = send(requestFor(REFRESH_URI, "http://app.finovago.com"));

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should not check the Origin on endpoints outside refresh and logout")
    void ignoresUnprotectedEndpoints() throws ServletException, IOException {
        MockHttpServletResponse response = send(requestFor(UNPROTECTED_URI, FOREIGN_ORIGIN));

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }
}
