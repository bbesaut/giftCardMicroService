package com.finovago.p2p.service;

import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.dto.LoginRequest;
import com.finovago.p2p.dto.RefreshTokenRequest;
import com.finovago.p2p.dto.RegisterRequest;
import com.finovago.p2p.exception.InvalidRefreshTokenException;
import com.finovago.p2p.exception.UserAlreadyExistsException;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.JwtService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found for email");
                    return new BadCredentialsException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Login failed - invalid password for user: {}", user.getEmail());
            throw new BadCredentialsException("Invalid credentials");
        }

        AuthResponse response = issueTokens(user);
        log.info("Tokens issued for user: {} (role: {})", user.getEmail(), user.getRole());
        return response;
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        try {
            User user = refreshTokenService.validateAndRotate(request.refreshToken());
            log.info("Token rotation successful for user: {}", user.getEmail());
            return issueTokens(user);
        } catch (InvalidRefreshTokenException e) {
            log.warn("Token rotation failed: {}", e.getMessage());
            throw e;
        }
    }

    public void logout(RefreshTokenRequest request) {
        try {
            refreshTokenService.revoke(request.refreshToken());
            log.info("Logout successful - refresh token revoked");
        } catch (InvalidRefreshTokenException e) {
            log.warn("Logout failed - refresh token not found or invalid");
            throw e;
        }
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            log.warn("Registration failed - email already exists: {}", request.email());
            throw new UserAlreadyExistsException("Email already registered");
        }

        Merchant merchant = merchantRepository.save(new Merchant(request.merchantName(), request.email()));

        User user = new User(request.email(), passwordEncoder.encode(request.password()), Role.MERCHANT, merchant);
        userRepository.save(user);
        log.info("User registered successfully: {} (role: MERCHANT, merchantId: {})", user.getEmail(), merchant.getId());
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        List<String> roles = List.of(user.getRole().name());
        Long merchantId = user.getMerchant() != null ? user.getMerchant().getId() : null;
        String accessToken = jwtService.generateToken(user.getEmail(), roles, merchantId);
        String refreshToken = refreshTokenService.createRefreshToken(user);
        log.debug("New access and refresh tokens generated for user: {}", user.getEmail());
        return new AuthResponse(accessToken, refreshToken);
    }
}
