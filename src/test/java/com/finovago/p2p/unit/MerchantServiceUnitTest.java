package com.finovago.p2p.unit;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.finovago.p2p.dto.MerchantResponse;
import com.finovago.p2p.dto.MerchantStatusResponse;
import com.finovago.p2p.dto.MerchantUserResponse;
import com.finovago.p2p.dto.RateLimitCapacityResponse;
import com.finovago.p2p.exception.MerchantNotFoundException;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.service.MerchantService;
import com.finovago.p2p.service.RefreshTokenService;

@ExtendWith(MockitoExtension.class)
class MerchantServiceUnitTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private MerchantService merchantService;

    private Merchant merchant(Long id) {
        Merchant merchant = new Merchant("Test Merchant", "merchant@example.com");
        ReflectionTestUtils.setField(merchant, "id", id);
        return merchant;
    }

    private User user(Long id, Merchant merchant, boolean owner) {
        User user = new User("user" + id + "@example.com", "hashed", Role.MERCHANT, merchant, owner);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void should_returnMappedMerchants_when_listingAllMerchants() {
        Merchant merchant = merchant(1L);
        merchant.setRateLimitCapacity(500);

        when(merchantRepository.findAllByOrderByIdAsc()).thenReturn(List.of(merchant));

        List<MerchantResponse> response = merchantService.listMerchants();

        assertEquals(1, response.size());
        assertEquals(new MerchantResponse(1L, "Test Merchant", "merchant@example.com", true, 500), response.get(0));
    }

    @Test
    void should_returnMappedUsers_when_listingMerchantUsers() {
        Merchant merchant = merchant(1L);
        User owner = user(10L, merchant, true);
        User employee = user(11L, merchant, false);
        employee.setActive(false);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(userRepository.findAllByMerchant_IdOrderByIdAsc(1L)).thenReturn(List.of(owner, employee));

        List<MerchantUserResponse> response = merchantService.listMerchantUsers(1L);

        assertEquals(2, response.size());
        assertEquals(new MerchantUserResponse(10L, "user10@example.com", true, true), response.get(0));
        assertEquals(new MerchantUserResponse(11L, "user11@example.com", false, false), response.get(1));
    }

    @Test
    void should_throwMerchantNotFoundException_when_listingUsersOfUnknownMerchant() {
        when(merchantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(MerchantNotFoundException.class, () -> merchantService.listMerchantUsers(99L));
        verify(userRepository, never()).findAllByMerchant_IdOrderByIdAsc(any());
    }

    @Test
    void should_deactivateMerchantAndRevokeAllUsersTokens_when_deactivating() {
        Merchant merchant = merchant(1L);
        User owner = user(10L, merchant, true);
        User employee = user(11L, merchant, false);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(userRepository.findAllByMerchant_IdOrderByIdAsc(1L)).thenReturn(List.of(owner, employee));

        MerchantStatusResponse response = merchantService.setMerchantActive(1L, false);

        assertFalse(response.active());
        assertFalse(merchant.isActive());
        verify(refreshTokenService).revokeAllForUser(owner);
        verify(refreshTokenService).revokeAllForUser(employee);
        verify(merchantRepository).save(merchant);
    }

    @Test
    void should_reactivateMerchantWithoutRevokingTokens_when_activating() {
        Merchant merchant = merchant(1L);
        merchant.setActive(false);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        MerchantStatusResponse response = merchantService.setMerchantActive(1L, true);

        assertTrue(response.active());
        assertTrue(merchant.isActive());
        verify(refreshTokenService, never()).revokeAllForUser(any());
        verify(userRepository, never()).findAllByMerchant_IdOrderByIdAsc(any());
    }

    @Test
    void should_throwMerchantNotFoundException_when_deactivatingUnknownMerchant() {
        when(merchantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(MerchantNotFoundException.class, () -> merchantService.setMerchantActive(99L, false));
    }

    @Test
    void should_setRateLimitCapacity_when_overrideProvided() {
        Merchant merchant = merchant(1L);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        RateLimitCapacityResponse response = merchantService.setRateLimitCapacity(1L, 750);

        assertEquals(new RateLimitCapacityResponse(1L, 750), response);
        assertEquals(750, merchant.getRateLimitCapacity());
        verify(merchantRepository).save(merchant);
    }

    @Test
    void should_clearRateLimitCapacityOverride_when_settingNull() {
        Merchant merchant = merchant(1L);
        merchant.setRateLimitCapacity(750);

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        RateLimitCapacityResponse response = merchantService.setRateLimitCapacity(1L, null);

        assertNull(response.rateLimitCapacity());
        assertNull(merchant.getRateLimitCapacity());
    }

    @Test
    void should_throwMerchantNotFoundException_when_settingRateLimitOfUnknownMerchant() {
        when(merchantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(MerchantNotFoundException.class, () -> merchantService.setRateLimitCapacity(99L, 100));
    }
}
