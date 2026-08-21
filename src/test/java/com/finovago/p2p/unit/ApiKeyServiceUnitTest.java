package com.finovago.p2p.unit;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.ApiKeyStatusResponse;
import com.finovago.p2p.model.ApiKey;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.ApiKeyRepository;
import com.finovago.p2p.service.ApiKeyService;

/**
 * No MockitoExtension/@InjectMocks: each test builds its own ApiKeyRepository mock and
 * ApiKeyService instance directly, since BCryptPasswordEncoder is real (needed for resolve()'s
 * secret-matching tests) rather than mocked.
 */
class ApiKeyServiceUnitTest {

    private Merchant merchant(Long id) {
        Merchant merchant = new Merchant("Test Merchant", "merchant@example.com");
        ReflectionTestUtils.setField(merchant, "id", id);
        return merchant;
    }

    @Test
    void should_createNewApiKey_when_merchantHasNoExistingKey() {
        ApiKeyRepository repo = org.mockito.Mockito.mock(ApiKeyRepository.class);
        ApiKeyService service = new ApiKeyService(repo, new BCryptPasswordEncoder());
        Merchant merchant = merchant(42L);

        when(repo.findByMerchant_Id(42L)).thenReturn(Optional.empty());

        ApiKeyResponse response = service.generateOrRotate(merchant);

        assertTrue(response.keyPrefix().startsWith("fovak_"));
        assertTrue(response.apiKeySecret().startsWith(response.keyPrefix() + "."));
        verify(repo).save(any(ApiKey.class));
    }

    @Test
    void should_rotateExistingApiKey_invalidatingThePreviousSecret_when_merchantAlreadyHasOne() {
        ApiKeyRepository repo = org.mockito.Mockito.mock(ApiKeyRepository.class);
        ApiKeyService service = new ApiKeyService(repo, new BCryptPasswordEncoder());
        Merchant merchant = merchant(42L);
        ApiKey existing = new ApiKey(merchant, "fovak_old", "old-hash");

        when(repo.findByMerchant_Id(42L)).thenReturn(Optional.of(existing));

        ApiKeyResponse response = service.generateOrRotate(merchant);

        assertFalse(response.keyPrefix().equals("fovak_old"));
        assertEquals(existing.getKeyPrefix(), response.keyPrefix());
        verify(repo).save(existing);
    }

    @Test
    void should_deactivateKey_when_revokingExistingKey() {
        ApiKeyRepository repo = org.mockito.Mockito.mock(ApiKeyRepository.class);
        ApiKeyService service = new ApiKeyService(repo, new BCryptPasswordEncoder());
        Merchant merchant = merchant(42L);
        ApiKey existing = new ApiKey(merchant, "fovak_abc", "hash");

        when(repo.findByMerchant_Id(42L)).thenReturn(Optional.of(existing));

        ApiKeyStatusResponse response = service.revoke(merchant);

        assertFalse(response.active());
        assertEquals("fovak_abc", response.keyPrefix());
        assertFalse(existing.isActive());
    }

    @Test
    void should_returnInactiveNullPrefix_when_revokingWithNoExistingKey() {
        ApiKeyRepository repo = org.mockito.Mockito.mock(ApiKeyRepository.class);
        ApiKeyService service = new ApiKeyService(repo, new BCryptPasswordEncoder());
        Merchant merchant = merchant(42L);

        when(repo.findByMerchant_Id(42L)).thenReturn(Optional.empty());

        ApiKeyStatusResponse response = service.revoke(merchant);

        assertFalse(response.active());
        assertEquals(null, response.keyPrefix());
        verify(repo, never()).save(any(ApiKey.class));
    }

    @Test
    void should_resolveToApiKey_when_presentedKeyMatchesAnActiveKey() {
        ApiKeyRepository repo = org.mockito.Mockito.mock(ApiKeyRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        ApiKeyService service = new ApiKeyService(repo, encoder);
        Merchant merchant = merchant(42L);
        String secret = "the-secret";
        ApiKey key = new ApiKey(merchant, "fovak_abc", encoder.encode(secret));

        when(repo.findByKeyPrefix("fovak_abc")).thenReturn(Optional.of(key));

        Optional<ApiKey> resolved = service.resolve("fovak_abc." + secret);

        assertTrue(resolved.isPresent());
        assertEquals(merchant, resolved.get().getMerchant());
        verify(repo).save(key);
    }

    @Test
    void should_returnEmpty_when_presentedKeyHasWrongSecret() {
        ApiKeyRepository repo = org.mockito.Mockito.mock(ApiKeyRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        ApiKeyService service = new ApiKeyService(repo, encoder);
        Merchant merchant = merchant(42L);
        ApiKey key = new ApiKey(merchant, "fovak_abc", encoder.encode("the-secret"));

        when(repo.findByKeyPrefix("fovak_abc")).thenReturn(Optional.of(key));

        Optional<ApiKey> resolved = service.resolve("fovak_abc.wrong-secret");

        assertTrue(resolved.isEmpty());
    }

    @Test
    void should_returnEmpty_when_keyIsRevoked() {
        ApiKeyRepository repo = org.mockito.Mockito.mock(ApiKeyRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        ApiKeyService service = new ApiKeyService(repo, encoder);
        Merchant merchant = merchant(42L);
        String secret = "the-secret";
        ApiKey key = new ApiKey(merchant, "fovak_abc", encoder.encode(secret));
        key.setActive(false);

        when(repo.findByKeyPrefix("fovak_abc")).thenReturn(Optional.of(key));

        Optional<ApiKey> resolved = service.resolve("fovak_abc." + secret);

        assertTrue(resolved.isEmpty());
    }

    @Test
    void should_returnEmpty_when_presentedKeyIsMalformed() {
        ApiKeyRepository repo = org.mockito.Mockito.mock(ApiKeyRepository.class);
        ApiKeyService service = new ApiKeyService(repo, new BCryptPasswordEncoder());

        assertTrue(service.resolve("no-separator").isEmpty());
        assertTrue(service.resolve(".no-prefix").isEmpty());
        assertTrue(service.resolve("no-secret.").isEmpty());
    }
}
