package com.finovago.p2p.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.security.CurrentUserContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RateLimitFilter Tests")
class RateLimitFilterTest {

    private static final int LOGIN_CAPACITY = 3;
    private static final int MERCHANT_CAPACITY = 3;
    private static final String LOGIN_URI = "/api/v1/auth/login";
    private static final String PROTECTED_URI = "/api/v1/giftcards/lookup/GC-12345";
    private static final String UNPROTECTED_URI = "/api/v1/giftcards/list";

    private CurrentUserContext currentUserContext;
    private MerchantRepository merchantRepository;
    private RateLimitFilter filter;
    private FilterChain filterChain;
    private AtomicInteger chainInvocations;

    @BeforeEach
    void setUp() {
        currentUserContext = mock(CurrentUserContext.class);
        merchantRepository = mock(MerchantRepository.class);
        when(merchantRepository.findById(any())).thenReturn(Optional.empty());

        filter = new RateLimitFilter(true, LOGIN_CAPACITY, MERCHANT_CAPACITY, 60, currentUserContext, merchantRepository, JsonMapper.builder().build());
        chainInvocations = new AtomicInteger();
        filterChain = (req, res) -> chainInvocations.incrementAndGet();
    }

    private MockHttpServletRequest requestFor(String uri, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    @DisplayName("Should allow login requests within capacity, keyed by IP")
    void allowsLoginRequestsWithinCapacity() throws ServletException, IOException {
        for (int i = 0; i < LOGIN_CAPACITY; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(requestFor(LOGIN_URI, "10.0.0.1"), response, filterChain);
            assertEquals(200, response.getStatus(), "Filter must not touch the response status when the request is allowed through");
        }
        assertEquals(LOGIN_CAPACITY, chainInvocations.get());
    }

    @Test
    @DisplayName("Should return 429 once the login quota is exceeded for the same IP")
    void blocksLoginRequestsBeyondCapacity() throws ServletException, IOException {
        for (int i = 0; i < LOGIN_CAPACITY; i++) {
            filter.doFilterInternal(requestFor(LOGIN_URI, "10.0.0.1"), new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(LOGIN_URI, "10.0.0.1"), blockedResponse, filterChain);

        assertEquals(429, blockedResponse.getStatus());
        assertEquals(LOGIN_CAPACITY, chainInvocations.get(), "Chain must not be invoked once the bucket is exhausted");
        assertNotNull(blockedResponse.getHeader("Retry-After"));
    }

    @Test
    @DisplayName("429 response body should not contain a correlation id/code field")
    void blockedResponseBodyHasNoCodeField() throws ServletException, IOException {
        for (int i = 0; i < LOGIN_CAPACITY; i++) {
            filter.doFilterInternal(requestFor(LOGIN_URI, "10.0.0.2"), new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(LOGIN_URI, "10.0.0.2"), blockedResponse, filterChain);

        JsonNode body = new ObjectMapper().readTree(blockedResponse.getContentAsString());
        assertEquals("Too Many Requests", body.get("error").asText());
        assertFalse(body.has("code"), "Error body should not duplicate the correlation id (already in X-Correlation-Id header)");
    }

    @Test
    @DisplayName("Should track separate login quotas per client IP")
    void tracksSeparateLoginQuotasPerIp() throws ServletException, IOException {
        for (int i = 0; i < LOGIN_CAPACITY; i++) {
            filter.doFilterInternal(requestFor(LOGIN_URI, "10.0.0.3"), new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(LOGIN_URI, "10.0.0.4"), otherIpResponse, filterChain);

        assertEquals(200, otherIpResponse.getStatus(), "A different IP must have its own, unexhausted quota");
        assertEquals(LOGIN_CAPACITY + 1, chainInvocations.get());
    }

    @Test
    @DisplayName("Should rate-limit lookup/redeem/reserve by merchant id, ignoring IP")
    void tracksQuotaByMerchantIdForProtectedEndpoints() throws ServletException, IOException {
        when(currentUserContext.currentMerchantId()).thenReturn(42L);

        for (int i = 0; i < MERCHANT_CAPACITY; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            // Different IP on every call - must not matter, only merchantId does.
            filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0." + i), response, filterChain);
            assertEquals(200, response.getStatus());
        }

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.99"), blockedResponse, filterChain);

        assertEquals(429, blockedResponse.getStatus(), "Same merchant, different IPs, must still share one quota");
        assertEquals(MERCHANT_CAPACITY, chainInvocations.get());
    }

    @Test
    @DisplayName("Should track separate quotas per merchant id")
    void tracksSeparateQuotasPerMerchant() throws ServletException, IOException {
        when(currentUserContext.currentMerchantId()).thenReturn(1L, 1L, 1L, 2L);

        for (int i = 0; i < MERCHANT_CAPACITY; i++) {
            filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.1"), new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletResponse otherMerchantResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.1"), otherMerchantResponse, filterChain);

        assertEquals(200, otherMerchantResponse.getStatus(), "A different merchant must have its own, unexhausted quota");
        assertEquals(MERCHANT_CAPACITY + 1, chainInvocations.get());
    }

    @Test
    @DisplayName("Should use the merchant's custom capacity override when set")
    void usesMerchantCapacityOverride() throws ServletException, IOException {
        when(currentUserContext.currentMerchantId()).thenReturn(7L);
        Merchant merchant = mock(Merchant.class);
        when(merchant.getRateLimitCapacity()).thenReturn(1);
        when(merchantRepository.findById(7L)).thenReturn(Optional.of(merchant));

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.1"), firstResponse, filterChain);
        assertEquals(200, firstResponse.getStatus());

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilterInternal(requestFor(PROTECTED_URI, "10.0.0.1"), secondResponse, filterChain);
        assertEquals(429, secondResponse.getStatus(), "Override capacity of 1 must be honored instead of the default");
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
        RateLimitFilter disabledFilter =
                new RateLimitFilter(false, LOGIN_CAPACITY, MERCHANT_CAPACITY, 60, currentUserContext, merchantRepository, JsonMapper.builder().build());

        // Go through doFilter() (not doFilterInternal directly) so shouldNotFilter() is actually consulted,
        // exactly like the real servlet container would.
        for (int i = 0; i < LOGIN_CAPACITY + 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            disabledFilter.doFilter(requestFor(LOGIN_URI, "10.0.0.6"), response, filterChain);
            assertEquals(200, response.getStatus());
        }
        assertEquals(LOGIN_CAPACITY + 5, chainInvocations.get());
    }
}
