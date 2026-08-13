package com.finovago.p2p.unit;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.finovago.p2p.model.Role;
import com.finovago.p2p.model.User;
import com.finovago.p2p.repository.UserRepository;
import com.finovago.p2p.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void loadUserByUsername_userExists_returnsUserPrincipalWithAuthorities() {
        User user = new User("merchant@example.com", "hashedPassword", Role.MERCHANT, null);
        when(userRepository.findByEmail("merchant@example.com")).thenReturn(Optional.of(user));

        UserDetails result = userService.loadUserByUsername("merchant@example.com");

        assertEquals("merchant@example.com", result.getUsername());
        assertEquals("hashedPassword", result.getPassword());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MERCHANT")));
    }

    @Test
    void loadUserByUsername_userDoesNotExist_throwsUsernameNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.loadUserByUsername("missing@example.com"));
    }
}
