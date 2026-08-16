package com.finovago.p2p.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.finovago.p2p.config.PostgresTestcontainerInitializer;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.RefreshTokenRepository;
import com.finovago.p2p.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hits a real embedded Tomcat over an actual socket (TestRestTemplate), unlike the rest of the
 * integration suite which uses MockMvc. This matters specifically for X-Response-Time: MockMvc's
 * MockHttpServletResponse never truly "commits" the way a real container does, so a header set in
 * ResponseTimeFilter's post-processing (after filterChain.doFilter() returns) can pass under
 * MockMvc while silently being dropped by a real server, once the response has actually been
 * committed. Only a real HTTP round-trip like this one can catch that class of regression.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestcontainerInitializer.class)
class RealServerResponseHeaderIntegrationTest {

    private static final String TEST_EMAIL = "real-server-test@example.com";
    private static final String TEST_PASSWORD = "testPassword123";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        Merchant merchant = merchantRepository.save(new Merchant("Test Merchant", "merchant@example.com"));
        userRepository.save(new User(TEST_EMAIL, passwordEncoder.encode(TEST_PASSWORD), Role.MERCHANT, merchant));
    }

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void should_include_response_time_header_on_real_socket_for_login() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(TEST_EMAIL, TEST_PASSWORD);

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl("/api/v1/auth/login"), new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String responseTime = response.getHeaders().getFirst("X-Response-Time");
        assertNotNull(responseTime, "X-Response-Time must be present on a real (non-MockMvc) response");
        assertTrue(responseTime.matches("\\d+"));
    }

    @Test
    void should_return_404_with_response_time_header_for_unmapped_route_even_when_authenticated() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String loginBody = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(TEST_EMAIL, TEST_PASSWORD);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(baseUrl("/api/v1/auth/login"), new HttpEntity<>(loginBody, headers), String.class);
        String token = loginResponse.getBody().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization", "Bearer " + token);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl("/api/v1/this/route/does/not/exist"), HttpMethod.GET, new HttpEntity<>(authHeaders), String.class);

        // Spring Boot forwards unmapped routes to /error internally; without /error being public,
        // JwtAuthenticationFilter never re-runs on that dispatch (OncePerRequestFilter skips ERROR by
        // default) and an authenticated caller would incorrectly get 401 instead of 404.
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getHeaders().getFirst("X-Response-Time"),
                "X-Response-Time must be present even on the /error-rendered 404");
    }
}
