package com.finovago.p2p.config;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.UserRepository;

import jakarta.annotation.PostConstruct;

@Component
@Profile("dev")
public class DataInitializer {

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, MerchantRepository merchantRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        if (userRepository.count() == 0) {
            Merchant demoMerchant = merchantRepository.save(new Merchant("Finovago Demo Merchant", "client@finovago.com"));

            userRepository.save(new User("admin@finovago.com", passwordEncoder.encode("admin123"), Role.ADMIN, null));
            userRepository.save(new User("client@finovago.com", passwordEncoder.encode("client123"), Role.MERCHANT, demoMerchant));
        }
    }
}
