package com.finovago.p2p.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finovago.p2p.dto.MerchantResponse;
import com.finovago.p2p.dto.MerchantStatusResponse;
import com.finovago.p2p.dto.MerchantUserResponse;
import com.finovago.p2p.dto.RateLimitCapacityResponse;
import com.finovago.p2p.exception.MerchantNotFoundException;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/** ADMIN-side merchant management: listing every tenant, activating/deactivating one, and overriding its rate limit. */
@Slf4j
@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public MerchantService(
            MerchantRepository merchantRepository,
            UserRepository userRepository,
            RefreshTokenService refreshTokenService) {
        this.merchantRepository = merchantRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<MerchantResponse> listMerchants() {
        return merchantRepository.findAllByOrderByIdAsc().stream()
                .map(merchant -> new MerchantResponse(
                        merchant.getId(), merchant.getName(), merchant.getContactEmail(),
                        merchant.isActive(), merchant.getRateLimitCapacity()))
                .toList();
    }

    /**
     * Deactivating also revokes every one of the merchant's users' active refresh tokens (immediate logout
     * on their next refresh attempt) and blocks new logins/API-key authentication (see AuthService.login,
     * AuthService.refresh, ApiKeyService.resolve). An access token already issued before deactivation stays
     * valid until its own ~15 min expiry - same trade-off already accepted for user-level deactivation.
     */
    @Transactional
    public MerchantStatusResponse setMerchantActive(Long merchantId, boolean active) {
        Merchant merchant = findMerchantOrThrow(merchantId);

        merchant.setActive(active);
        merchantRepository.save(merchant);

        if (!active) {
            userRepository.findAllByMerchant_IdOrderByIdAsc(merchantId)
                    .forEach(refreshTokenService::revokeAllForUser);
        }

        log.info("Merchant {} (id: {}) {}", merchant.getName(), merchant.getId(), active ? "reactivated" : "deactivated");

        return new MerchantStatusResponse(merchant.getId(), merchant.getName(), merchant.isActive());
    }

    /** Lists a given merchant's human user accounts, for the ADMIN merchant-detail screen. */
    @Transactional(readOnly = true)
    public List<MerchantUserResponse> listMerchantUsers(Long merchantId) {
        findMerchantOrThrow(merchantId);

        return userRepository.findAllByMerchant_IdOrderByIdAsc(merchantId).stream()
                .map(user -> new MerchantUserResponse(user.getId(), user.getEmail(), user.isOwner(), user.isActive()))
                .toList();
    }

    /** Null clears the override, falling back to app.rate-limit.merchant-capacity. */
    @Transactional
    public RateLimitCapacityResponse setRateLimitCapacity(Long merchantId, Integer rateLimitCapacity) {
        Merchant merchant = findMerchantOrThrow(merchantId);

        merchant.setRateLimitCapacity(rateLimitCapacity);
        merchantRepository.save(merchant);

        log.info("Rate limit capacity for merchant {} (id: {}) set to {}", merchant.getName(), merchant.getId(), rateLimitCapacity);

        return new RateLimitCapacityResponse(merchant.getId(), merchant.getRateLimitCapacity());
    }

    private Merchant findMerchantOrThrow(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + merchantId));
    }
}
