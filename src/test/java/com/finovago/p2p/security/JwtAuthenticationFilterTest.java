package com.finovago.p2p.security;

import java.util.List;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, RoleHierarchyImpl.withDefaultRolePrefix().build(), JsonMapper.builder().build());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should pass through the chain without touching JwtService when there is no Authorization header")
    void testNoAuthorizationHeaderPassesThrough() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Should pass through the chain without touching JwtService when the header is not a Bearer token")
    void testNonBearerAuthorizationHeaderPassesThrough() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Should write a 401 JSON response instead of propagating the exception when the token signature is invalid")
    void testInvalidSignatureReturns401InsteadOfPropagating() throws Exception {
        request.addHeader("Authorization", "Bearer tampered.token.value");
        when(jwtService.extractUsername("tampered.token.value")).thenThrow(mock(SignatureException.class));

        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid token"));
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Should not re-authenticate when the security context already holds an authentication")
    void testAlreadyAuthenticatedSkipsReauthentication() throws Exception {
        request.addHeader("Authorization", "Bearer valid.token.value");
        when(jwtService.extractUsername("valid.token.value")).thenReturn("client@example.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("already-authenticated", null, List.of()));

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtService, never()).isTokenValid(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not authenticate when the token is no longer valid")
    void testInvalidTokenDoesNotAuthenticate() throws Exception {
        request.addHeader("Authorization", "Bearer stale.token.value");
        when(jwtService.extractUsername("stale.token.value")).thenReturn("client@example.com");
        when(jwtService.isTokenValid("stale.token.value")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should fail closed and not authenticate a MERCHANT token missing merchantId")
    void testMerchantTokenMissingMerchantIdFailsClosed() throws Exception {
        request.addHeader("Authorization", "Bearer merchant.no.tenant");
        when(jwtService.extractUsername("merchant.no.tenant")).thenReturn("client@example.com");
        when(jwtService.isTokenValid("merchant.no.tenant")).thenReturn(true);
        when(jwtService.extractRoles("merchant.no.tenant")).thenReturn(List.of("MERCHANT"));
        when(jwtService.extractMerchantId("merchant.no.tenant")).thenReturn(null);
        when(jwtService.extractUserId("merchant.no.tenant")).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should authenticate a valid MERCHANT token that carries a merchantId")
    void testValidMerchantTokenAuthenticates() throws Exception {
        request.addHeader("Authorization", "Bearer merchant.with.tenant");
        when(jwtService.extractUsername("merchant.with.tenant")).thenReturn("client@example.com");
        when(jwtService.isTokenValid("merchant.with.tenant")).thenReturn(true);
        when(jwtService.extractRoles("merchant.with.tenant")).thenReturn(List.of("MERCHANT"));
        when(jwtService.extractMerchantId("merchant.with.tenant")).thenReturn(42L);
        when(jwtService.extractUserId("merchant.with.tenant")).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication != null && authentication.isAuthenticated());
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        assertEquals("client@example.com", principal.email());
        assertEquals(42L, principal.merchantId());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should authenticate a valid ADMIN token even without a merchantId")
    void testValidAdminTokenAuthenticatesWithoutMerchantId() throws Exception {
        request.addHeader("Authorization", "Bearer admin.token.value");
        when(jwtService.extractUsername("admin.token.value")).thenReturn("admin@example.com");
        when(jwtService.isTokenValid("admin.token.value")).thenReturn(true);
        when(jwtService.extractRoles("admin.token.value")).thenReturn(List.of("ADMIN"));
        when(jwtService.extractMerchantId("admin.token.value")).thenReturn(null);
        when(jwtService.extractUserId("admin.token.value")).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication != null && authentication.isAuthenticated());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should write a 401 JSON response instead of propagating the exception when the token is expired")
    void testExpiredTokenReturns401InsteadOfPropagating() throws Exception {
        request.addHeader("Authorization", "Bearer expired.token.value");
        when(jwtService.extractUsername("expired.token.value")).thenThrow(mock(ExpiredJwtException.class));

        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Token has expired"));
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Should write a 401 JSON response instead of propagating the exception when the token is malformed")
    void testMalformedTokenReturns401InsteadOfPropagating() throws Exception {
        request.addHeader("Authorization", "Bearer garbage");
        when(jwtService.extractUsername("garbage")).thenThrow(mock(MalformedJwtException.class));

        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, filterChain));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid token"));
        verifyNoInteractions(filterChain);
    }
}
