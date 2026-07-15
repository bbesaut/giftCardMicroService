package com.finovago.p2p.unit;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.finovago.p2p.security.JwtService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceUnitTest {

    private static final String SECRET_KEY = "my-secret-key-that-is-very-very-long-and-secure-for-testing";
    private static final long EXPIRATION_MS = 900_000; // 15 minutes
    private static final String TEST_USERNAME = "user@example.com";

    private JwtService jwtService;
    private SecretKey testKey;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET_KEY, EXPIRATION_MS);
        testKey = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void should_generateValidToken_with_usernameAndRoles() {
        List<String> roles = List.of("CLIENT", "ADMIN");

        String token = jwtService.generateToken(TEST_USERNAME, roles);

        assertNotNull(token);
        assertTrue(token.length() > 0);
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void should_extractUsername_from_validToken() {
        List<String> roles = List.of("CLIENT");
        String token = jwtService.generateToken(TEST_USERNAME, roles);

        String extractedUsername = jwtService.extractUsername(token);

        assertEquals(TEST_USERNAME, extractedUsername);
    }

    @Test
    void should_extractRoles_from_validToken() {
        List<String> roles = List.of("CLIENT", "ADMIN");
        String token = jwtService.generateToken(TEST_USERNAME, roles);

        List<String> extractedRoles = jwtService.extractRoles(token);

        assertEquals(roles, extractedRoles);
    }

    @Test
    void should_extractSingleRole_from_validToken() {
        List<String> roles = List.of("ADMIN");
        String token = jwtService.generateToken(TEST_USERNAME, roles);

        List<String> extractedRoles = jwtService.extractRoles(token);

        assertEquals(1, extractedRoles.size());
        assertEquals("ADMIN", extractedRoles.get(0));
    }

    @Test
    void should_validateToken_when_tokenIsValid() {
        String token = jwtService.generateToken(TEST_USERNAME, List.of("CLIENT"));

        boolean isValid = jwtService.isTokenValid(token);

        assertTrue(isValid);
    }

    @Test
    void should_invalidateToken_when_tokenIsExpired() {
        String expiredToken = Jwts.builder()
                .subject(TEST_USERNAME)
                .claim("roles", List.of("CLIENT"))
                .issuedAt(new Date(System.currentTimeMillis() - EXPIRATION_MS - 1000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(testKey)
                .compact();

        boolean isValid = jwtService.isTokenValid(expiredToken);

        assertFalse(isValid);
    }

    @Test
    void should_returnFalse_when_tokenIsMalformed() {
        String malformedToken = "invalid.token.format";

        boolean isValid = jwtService.isTokenValid(malformedToken);

        assertFalse(isValid);
    }

    @Test
    void should_returnFalse_when_tokenIsEmpty() {
        boolean isValid = jwtService.isTokenValid("");

        assertFalse(isValid);
    }

    @Test
    void should_returnFalse_when_tokenIsNull() {
        boolean isValid = jwtService.isTokenValid(null);

        assertFalse(isValid);
    }

    @Test
    void should_returnFalse_when_tokenHasWrongSignature() {
        String tokenWithWrongSignature = Jwts.builder()
                .subject(TEST_USERNAME)
                .claim("roles", List.of("CLIENT"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(Keys.hmacShaKeyFor("different-secret-key-that-is-very-long".getBytes(StandardCharsets.UTF_8)))
                .compact();

        boolean isValid = jwtService.isTokenValid(tokenWithWrongSignature);

        assertFalse(isValid);
    }

    @Test
    void should_generateTokenWith_correctExpirationTime() {
        long beforeGeneration = System.currentTimeMillis();
        String token = jwtService.generateToken(TEST_USERNAME, List.of("CLIENT"));
        long afterGeneration = System.currentTimeMillis();

        Claims claims = Jwts.parser()
                .verifyWith(testKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        long expirationTime = claims.getExpiration().getTime();
        long expectedMinExpiration = beforeGeneration + EXPIRATION_MS;
        long expectedMaxExpiration = afterGeneration + EXPIRATION_MS;

        assertTrue(expirationTime >= expectedMinExpiration && expirationTime <= expectedMaxExpiration);
    }

    @Test
    void should_handleMultipleRoles_correctly() {
        List<String> roles = List.of("ADMIN", "CLIENT", "USER");
        String token = jwtService.generateToken(TEST_USERNAME, roles);

        List<String> extractedRoles = jwtService.extractRoles(token);

        assertEquals(3, extractedRoles.size());
        assertTrue(extractedRoles.containsAll(roles));
    }

    @Test
    void should_handleEmptyRolesList_correctly() {
        List<String> emptyRoles = List.of();
        String token = jwtService.generateToken(TEST_USERNAME, emptyRoles);

        List<String> extractedRoles = jwtService.extractRoles(token);

        assertTrue(extractedRoles.isEmpty());
    }
}
