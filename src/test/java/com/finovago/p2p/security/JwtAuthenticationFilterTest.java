package com.finovago.p2p.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        filter = new JwtAuthenticationFilter(jwtService, RoleHierarchyImpl.withDefaultRolePrefix().build());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
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
