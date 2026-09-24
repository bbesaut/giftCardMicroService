package com.finovago.p2p.unit;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.finovago.p2p.dto.ApiKeyResponse;
import com.finovago.p2p.dto.ApiKeyStatusResponse;
import com.finovago.p2p.dto.AuthResponse;
import com.finovago.p2p.dto.ChangePasswordRequest;
import com.finovago.p2p.dto.CurrentUserResponse;
import com.finovago.p2p.dto.LoginRequest;
import com.finovago.p2p.dto.MerchantUserResponse;
import com.finovago.p2p.dto.RefreshTokenRequest;
import com.finovago.p2p.dto.RegisterRequest;
import com.finovago.p2p.dto.UserStatusResponse;
import com.finovago.p2p.exception.InactiveAccountException;
import com.finovago.p2p.exception.OwnerPrivilegeRequiredException;
import com.finovago.p2p.exception.SamePasswordException;
import com.finovago.p2p.exception.SelfDeactivationException;
import com.finovago.p2p.exception.ServiceAccountNotAllowedException;
import com.finovago.p2p.exception.UserAlreadyExistsException;
import com.finovago.p2p.exception.UserNotFoundException;
import com.finovago.p2p.model.Merchant;
import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.MerchantRepository;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.security.JwtService;
import com.finovago.p2p.service.ApiKeyService;
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

    @Mock
    private ApiKeyService apiKeyService;

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
    void should_returnOwnerAuthResponse_when_registrationSucceeds() {
        RegisterRequest request = new RegisterRequest("newuser@example.com", "password123", "Acme Corp");

        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());
        when(merchantRepository.save(any(Merchant.class))).thenReturn(merchant());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(jwtService.generateToken(eq("newuser@example.com"), anyList(), any(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        verify(merchantRepository).save(any(Merchant.class));
        verify(userRepository).save(argThat(saved -> saved.getEmail().equals("newuser@example.com") && saved.isOwner()));
        verify(userRepository, org.mockito.Mockito.times(1)).save(any(User.class));
    }

    @Test
    void should_generateApiKeyForOwnersMerchant_when_ownerRequestsOne() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);
        ApiKeyResponse expected = new ApiKeyResponse("fovak_abc", "fovak_abc.secret");

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(apiKeyService.generateOrRotate(merchant)).thenReturn(expected);

        ApiKeyResponse response = authService.generateApiKey(1L);

        assertEquals(expected, response);
    }

    @Test
    void should_throwOwnerPrivilegeRequiredException_when_nonOwnerRequestsApiKey() {
        Merchant merchant = merchant();
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 2L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThrows(OwnerPrivilegeRequiredException.class, () -> authService.generateApiKey(2L));
    }

    @Test
    void should_revokeApiKey_when_ownerRevokes() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);
        ApiKeyStatusResponse expected = new ApiKeyStatusResponse("fovak_abc", false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(apiKeyService.revoke(merchant)).thenReturn(expected);

        ApiKeyStatusResponse response = authService.revokeApiKey(1L);

        assertEquals(expected, response);
    }

    @Test
    void should_throwUserAlreadyExistsException_when_emailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "password123", "Acme Corp");
        User existingUser = new User("existing@example.com", "hashed", Role.MERCHANT, merchant());

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
    }

    private User owner(Long id, Merchant merchant) {
        User owner = new User("owner@example.com", "hashed", Role.MERCHANT, merchant, true);
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
        verify(userRepository).save(argThat(saved -> saved.getMerchant() == merchant && !saved.isOwner()));
    }

    @Test
    void should_throwOwnerPrivilegeRequiredException_when_callerIsNotOwner() {
        Merchant merchant = merchant();
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 2L);
        AddMerchantUserRequest request = new AddMerchantUserRequest("newguy@example.com", "password123");

        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThrows(OwnerPrivilegeRequiredException.class, () -> authService.addUserToOwnMerchant(2L, request));
    }

    @Test
    void should_returnMappedUsers_when_ownerListsOwnMerchant() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 2L);
        employee.setActive(false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findAllByMerchant_IdOrderByIdAsc(null)).thenReturn(List.of(owner, employee));

        List<MerchantUserResponse> response = authService.listMyUsers(1L);

        assertEquals(2, response.size());
        assertEquals(new MerchantUserResponse(1L, "owner@example.com", true, true), response.get(0));
        assertEquals(new MerchantUserResponse(2L, "employee@example.com", false, false), response.get(1));
    }

    @Test
    void should_throwOwnerPrivilegeRequiredException_when_nonOwnerListsUsers() {
        Merchant merchant = merchant();
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 2L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThrows(OwnerPrivilegeRequiredException.class, () -> authService.listMyUsers(2L));
    }

    @Test
    void should_deactivateUserAndRevokeTokens_when_ownerDeactivatesEmployee() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false);
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
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false);
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
    void should_throwUserNotFoundException_when_targetNotInCallersMerchant() {
        Merchant merchant = merchant();
        User owner = owner(1L, merchant);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndMerchant_Id(99L, null)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> authService.setUserActive(1L, 99L, false));
    }

    @Test
    void should_changePasswordAndRevokeTokens_when_currentPasswordMatches() {
        Merchant merchant = merchant();
        User user = owner(1L, merchant);
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass123!", "NewPass456!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass123!", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("NewPass456!")).thenReturn("newHashed");

        authService.changePassword(1L, request);

        assertEquals("newHashed", user.getPassword());
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    void should_throwBadCredentialsException_when_currentPasswordIsWrong() {
        Merchant merchant = merchant();
        User user = owner(1L, merchant);
        ChangePasswordRequest request = new ChangePasswordRequest("wrongPass", "NewPass456!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "hashed")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.changePassword(1L, request));
    }

    @Test
    void should_throwSamePasswordException_when_newPasswordEqualsCurrentPassword() {
        Merchant merchant = merchant();
        User user = owner(1L, merchant);
        ChangePasswordRequest request = new ChangePasswordRequest("samePass123!", "samePass123!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("samePass123!", "hashed")).thenReturn(true);

        assertThrows(SamePasswordException.class, () -> authService.changePassword(1L, request));
    }

    @Test
    void should_throwServiceAccountNotAllowedException_when_callerIdIsNull() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass123!", "NewPass456!");

        assertThrows(ServiceAccountNotAllowedException.class, () -> authService.changePassword(null, request));
    }

    @Test
    void should_returnOwnerProfileWithMerchant_when_getCurrentUserCalledByOwner() {
        Merchant merchant = merchant();
        org.springframework.test.util.ReflectionTestUtils.setField(merchant, "id", 10L);
        User owner = owner(1L, merchant);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        CurrentUserResponse response = authService.getCurrentUser(1L);

        assertEquals(1L, response.userId());
        assertEquals("owner@example.com", response.email());
        assertEquals("MERCHANT", response.role());
        assertTrue(response.owner());
        assertEquals(new CurrentUserResponse.MerchantSummary(10L, "Test Merchant"), response.merchant());
    }

    @Test
    void should_returnNonOwnerProfile_when_getCurrentUserCalledByEmployee() {
        Merchant merchant = merchant();
        User employee = new User("employee@example.com", "hashed", Role.MERCHANT, merchant, false);
        org.springframework.test.util.ReflectionTestUtils.setField(employee, "id", 2L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        CurrentUserResponse response = authService.getCurrentUser(2L);

        assertFalse(response.owner());
        assertEquals("MERCHANT", response.role());
    }

    @Test
    void should_returnProfileWithoutMerchant_when_getCurrentUserCalledByAdmin() {
        User admin = new User("admin@example.com", "hashed", Role.ADMIN, null);
        org.springframework.test.util.ReflectionTestUtils.setField(admin, "id", 3L);

        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));

        CurrentUserResponse response = authService.getCurrentUser(3L);

        assertEquals("ADMIN", response.role());
        assertFalse(response.owner());
        assertNull(response.merchant());
    }

    @Test
    void should_throwServiceAccountNotAllowedException_when_getCurrentUserCalledWithNullId() {
        assertThrows(ServiceAccountNotAllowedException.class, () -> authService.getCurrentUser(null));
    }

    @Test
    void should_throwInactiveAccountException_when_getCurrentUserCalledForUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(InactiveAccountException.class, () -> authService.getCurrentUser(99L));
    }

    @Test
    void should_throwInactiveAccountException_when_getCurrentUserCalledForDeactivatedUser() {
        User deactivated = owner(1L, merchant());
        deactivated.setActive(false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(deactivated));

        assertThrows(InactiveAccountException.class, () -> authService.getCurrentUser(1L));
    }
}
