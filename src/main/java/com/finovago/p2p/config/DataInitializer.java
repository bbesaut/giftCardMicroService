package com.finovago.p2p.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.service.ApiKeyService;

import jakarta.annotation.PostConstruct;

@Component
@Profile("dev")
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyService apiKeyService;

    public DataInitializer(UserRepository userRepository, MerchantRepository merchantRepository,
            PasswordEncoder passwordEncoder, ApiKeyService apiKeyService) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.apiKeyService = apiKeyService;
    }

    @PostConstruct
    public void init() {
        if (userRepository.count() == 0) {
            Merchant demoMerchant = merchantRepository.save(new Merchant("Finovago Demo Merchant", "client@finovago.com"));

            userRepository.save(new User("admin@finovago.com", passwordEncoder.encode("admin123"), Role.ADMIN, null));
            userRepository.save(new User("client@finovago.com", passwordEncoder.encode("client123"), Role.MERCHANT, demoMerchant, true));

            // An API key is its own identity, directly on the merchant - no "service account" User
            // involved. Seeded here so /credit's 403 for API-key callers is exercisable locally
            // without calling POST /me/api-key first.
            ApiKeyResponse apiKey = apiKeyService.generateOrRotate(demoMerchant);
            log.info("Demo merchant API key (dev only): {}", apiKey.apiKeySecret());
        }
    }
}
