package com.finovago.p2p.unit;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.finovago.p2p.dto.AddMerchantUserRequest;
import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.dto.LoginRequest;
import com.finovago.p2p.dto.RefreshTokenRequest;
import com.finovago.p2p.dto.RegisterRequest;
import com.finovago.p2p.dto.RegisterResponse;
import com.finovago.p2p.dto.UserStatusResponse;
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
        when(jwtService.generateToken(eq("client@example.com"), anyList(), any(), any())).thenReturn("access-token");
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
    void should_throwBadCredentialsException_when_accountDeactivated() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, merchant());
        user.setActive(false);
        LoginRequest request = new LoginRequest("client@example.com", "password123");

        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void should_returnNewAuthResponse_when_refreshSucceeds() {
        User user = new User("client@example.com", "hashed", Role.MERCHANT, merchant());
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");

        when(refreshTokenService.validateAndRotate("old-refresh-token")).thenReturn(user);
        when(jwtService.generateToken(eq("client@example.com"), anyList(), any(), any())).thenReturn("new-access-token");
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
    void should_returnRegisterResponseWithOwnerAndServiceAccount_when_registrationSucceeds() {
        RegisterRequest request = new RegisterRequest("newuser@example.com", "password123", "Acme Corp");

        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtService.generateToken(eq("newuser@example.com"), anyList(), any(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");

        RegisterResponse response = authService.register(request);

        assertEquals("access-token", response.owner().accessToken());
        assertEquals("refresh-token", response.owner().refreshToken());
        // merchant().getId() is null here since the mocked Merchant is never actually persisted.
        assertEquals("finovago_service_account+null@example.com", response.serviceAccountEmail());
        verify(merchantRepository).save(any(Merchant.class));
        verify(userRepository).save(argThat(saved -> saved.getEmail().equals("newuser@example.com") && saved.isOwner() && !saved.isServiceAccount()));
        verify(userRepository).save(argThat(saved -> saved.isServiceAccount() && !saved.isOwner()));
    }

    @Test
    void should_throwUserAlreadyExistsException_when_emailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "password123", "Acme Corp");
        User existingUser = new User("existing@example.com", "hashed", Role.MERCHANT, merchant());

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
    }

    private User owner(Long id, Merchant merchant) {
        User owner = new User("owner@example.com", "hashed", Role.MERCHANT, merchant, false, true);
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "id", id);
        return owner;
    }

    @Test
    void should_returnAuthResponse_when_ownerAddsUserToOwnMerchant() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);
        AddMerchantUserRequest request = new AddMerchantUserRequest("employee@example.com", "password123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail("employee@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateToken(eq("employee@example.com"), anyList(), any(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.addUserToOwnMerchant(1L, request);

        assertEquals("access-token", response.accessToken());
        verify(userRepository).save(argThat(saved -> saved.getMerchant() == merchant && !saved.isOwner() && !saved.isServiceAccount()));
    }

    @Test
    void should_throwOwnerPrivilegeRequiredException_when_callerIsNotOwner() {
        Merchant merchant = merchant();
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false, false);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 2L);
        AddMerchantUserRequest request = new AddMerchantUserRequest("newguy@example.com", "password123");

        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThrows(OwnerPrivilegeRequiredException.class, () -> authService.addUserToOwnMerchant(2L, request));
    }

    @Test
    void should_throwOwnerPrivilegeRequiredException_when_callerIsServiceAccount() {
        Merchant merchant = merchant();
        User serviceAccount = new User("svc@example.com", "hashed", Role.MERCHANT, merchant, true, false);
        org.springframework.test.util.ReflectionTestUtils.setField(serviceAccount, "id", 3L);
        AddMerchantUserRequest request = new AddMerchantUserRequest("newguy@example.com", "password123");

        when(userRepository.findById(3L)).thenReturn(Optional.of(serviceAccount));

        assertThrows(OwnerPrivilegeRequiredException.class, () -> authService.addUserToOwnMerchant(3L, request));
    }

    @Test
    void should_deactivateUserAndRevokeTokens_when_ownerDeactivatesEmployee() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false, false);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndMerchant_Id(2L, null)).thenReturn(Optional.of(employee));

        UserStatusResponse response = authService.setUserActive(1L, 2L, false);

        assertFalse(response.active());
        assertFalse(employee.isActive());
        verify(refreshTokenService).revokeAllForUser(employee);
    }

    @Test
    void should_reactivateUserWithoutRevokingTokens_when_ownerReactivatesEmployee() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false, false);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 2L);
        employee.setActive(false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndMerchant_Id(2L, null)).thenReturn(Optional.of(employee));

        UserStatusResponse response = authService.setUserActive(1L, 2L, true);

        assertTrue(response.active());
        verify(refreshTokenService, org.mockito.Mockito.never()).revokeAllForUser(any());
    }

    @Test
    void should_throwSelfDeactivationException_when_ownerDeactivatesSelf() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        assertThrows(SelfDeactivationException.class, () -> authService.setUserActive(1L, 1L, false));
    }

    @Test
    void should_deactivateServiceAccountAndRevokeTokens_when_ownerDeactivatesIt() {
        // Deliberately allowed - lets an owner cut access immediately if the service account's
        // credentials leak, even though it breaks the merchant's live integration until re-enabled.
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);
        User serviceAccount = new User("svc@example.com", "hashed", Role.MERCHANT, merchant, true, false);
        org.springframework.test.util.ReflectionTestUtils.setField(serviceAccount, "id", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndMerchant_Id(2L, null)).thenReturn(Optional.of(serviceAccount));

        UserStatusResponse response = authService.setUserActive(1L, 2L, false);

        assertFalse(response.active());
        verify(refreshTokenService).revokeAllForUser(serviceAccount);
    }

    @Test
    void should_throwUserNotFoundException_when_targetNotInCallersMerchant() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndMerchant_Id(99L, null)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> authService.setUserActive(1L, 99L, false));
    }
}
