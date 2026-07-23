package com.finovago.p2p.unit;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.dto.LoginRequest;
import com.finovago.p2p.dto.RefreshTokenRequest;
import com.finovago.p2p.dto.RegisterRequest;
import com.finovago.p2p.exception.UserAlreadyExistsException;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.JwtService;
import com.finovago.p2p.service.AuthService;
import com.finovago.p2p.service.RefreshTokenService;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private Merchant merchant() {
        return new Merchant("Test Merchant", "merchant@example.com");
    }

    @Test
    void should_returnAuthResponse_when_loginSucceeds() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, merchant());
        LoginRequest request = new LoginRequest("client@example.com", "password123");

        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(eq("client@example.com"), anyList(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
    }

    @Test
    void should_throwBadCredentialsException_when_emailNotFound() {
        LoginRequest request = new LoginRequest("unknown@example.com", "password123");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void should_throwBadCredentialsException_when_passwordWrong() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, merchant());
        LoginRequest request = new LoginRequest("client@example.com", "wrong-password");

        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void should_returnNewAuthResponse_when_refreshSucceeds() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, merchant());
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");

        when(refreshTokenService.validateAndRotate("old-refresh-token")).thenReturn(user);
        when(jwtService.generateToken(eq("client@example.com"), anyList(), any())).thenReturn("new-access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("new-refresh-token");

        AuthResponse response = authService.refresh(request);

        assertEquals("new-access-token", response.accessToken());
        assertEquals("new-refresh-token", response.refreshToken());
    }

    @Test
    void should_revokeToken_when_logoutCalled() {
        RefreshTokenRequest request = new RefreshTokenRequest("some-refresh-token");

        authService.logout(request);

        verify(refreshTokenService).revoke("some-refresh-token");
    }

    @Test
    void should_returnAuthResponse_when_registrationSucceeds() {
        RegisterRequest request = new RegisterRequest("newuser@example.com", "password123", "Acme Corp");

        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateToken(eq("newuser@example.com"), anyList(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        verify(merchantRepository).save(any(Merchant.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void should_throwUserAlreadyExistsException_when_emailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "password123", "Acme Corp");
        User existingUser = new User("existing@example.com", "hashed", Role.MERCHANT, merchant());

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
    }
}
