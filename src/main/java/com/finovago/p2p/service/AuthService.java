package com.finovago.p2p.service;

import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.finovago.p2p.dto.AddMerchantUserRequest;
import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.ApiKeyStatusResponse;
import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.dto.LoginRequest;
import com.finovago.p2p.dto.RefreshTokenRequest;
import com.finovago.p2p.dto.RegisterRequest;
import com.finovago.p2p.dto.UserStatusResponse;
import com.finovago.p2p.exception.InvalidRefreshTokenException;
import com.finovago.p2p.exception.OwnerPrivilegeRequiredException;
import com.finovago.p2p.exception.SelfDeactivationException;
import com.finovago.p2p.exception.UserAlreadyExistsException;
import com.finovago.p2p.exception.UserNotFoundException;
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
    private final ApiKeyService apiKeyService;

    public AuthService(
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            ApiKeyService apiKeyService) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.apiKeyService = apiKeyService;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found for email");
                    return new BadCredentialsException("Invalid credentials");
                });

        long start = System.nanoTime();
        boolean matches = passwordEncoder.matches(request.password(), user.getPassword());
        log.debug("BCrypt matches took {} ms", (System.nanoTime() - start) / 1_000_000);

        if (!matches) {
            log.warn("Login failed - invalid password for user: {}", user.getEmail());
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!user.isActive()) {
            log.warn("Login failed - account deactivated for user: {}", user.getEmail());
            throw new BadCredentialsException("Invalid credentials");
        }

        AuthResponse response = issueTokens(user);
        log.info("Tokens issued for user: {} (role: {})", user.getEmail(), user.getRole());
        return response;
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        try {
            User user = refreshTokenService.validateAndRotate(request.refreshToken());
            if (!user.isActive()) {
                log.warn("Token rotation failed - account deactivated for user: {}", user.getEmail());
                throw new InvalidRefreshTokenException("Account has been deactivated");
            }
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

        // The submitted credentials belong to the human owner: the account that can log in,
        // manage the merchant's other users, and use owner-only endpoints like /credit. Automated
        // integration access is a separate concept entirely (API key, see ApiKeyService) - the
        // owner requests one explicitly via POST /me/api-key whenever they actually need it.
        User owner = new User(request.email(), passwordEncoder.encode(request.password()), Role.MERCHANT, merchant, true);
        userRepository.save(owner);

        log.info("Merchant registered successfully: merchantId: {}, owner: {}", merchant.getId(), owner.getEmail());

        return issueTokens(owner);
    }

    /** Generates the merchant's first API key, or rotates it (invalidating the old one) if it already has one. */
    public ApiKeyResponse generateApiKey(Long callerId) {
        User caller = requireOwner(callerId);
        return apiKeyService.generateOrRotate(caller.getMerchant());
    }

    public ApiKeyStatusResponse revokeApiKey(Long callerId) {
        User caller = requireOwner(callerId);
        return apiKeyService.revoke(caller.getMerchant());
    }

    public AuthResponse addUserToOwnMerchant(Long callerId, AddMerchantUserRequest request) {
        User caller = requireOwner(callerId);

        if (userRepository.findByEmail(request.email()).isPresent()) {
            log.warn("Adding merchant user failed - email already exists: {}", request.email());
            throw new UserAlreadyExistsException("Email already registered");
        }

        User user = new User(request.email(), passwordEncoder.encode(request.password()), Role.MERCHANT, caller.getMerchant());
        userRepository.save(user);
        log.info("Employee self-added by owner {} to merchantId: {}", caller.getEmail(), caller.getMerchant().getId());
        return issueTokens(user);
    }

    public UserStatusResponse setUserActive(Long callerId, Long userId, boolean active) {
        User caller = requireOwner(callerId);

        if (!active && userId.equals(caller.getId())) {
            throw new SelfDeactivationException("An owner cannot deactivate their own account");
        }

        User target = userRepository.findByIdAndMerchant_Id(userId, caller.getMerchant().getId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        target.setActive(active);
        userRepository.save(target);

        if (!active) {
            refreshTokenService.revokeAllForUser(target);
        }

        log.info("User {} (id: {}) {} by owner {}", target.getEmail(), target.getId(),
                active ? "reactivated" : "deactivated", caller.getEmail());

        return new UserStatusResponse(target.getId(), target.getEmail(), target.isActive());
    }

    private User requireOwner(Long callerId) {
        User caller = callerId == null ? null : userRepository.findById(callerId).orElse(null);
        if (caller == null || !caller.isOwner() || !caller.isActive()) {
            throw new OwnerPrivilegeRequiredException("Only the merchant's owner account can perform this action");
        }
        return caller;
    }

    private AuthResponse issueTokens(User user) {
        List<String> roles = List.of(user.getRole().name());
        Long merchantId = user.getMerchant() != null ? user.getMerchant().getId() : null;
        String accessToken = jwtService.generateToken(user.getEmail(), roles, merchantId, user.getId());
        String refreshToken = refreshTokenService.createRefreshToken(user);
        log.debug("New access and refresh tokens generated for user: {}", user.getEmail());
        return new AuthResponse(accessToken, refreshToken);
    }
}
