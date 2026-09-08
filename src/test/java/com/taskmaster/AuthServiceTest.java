package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.taskmaster.domain.User;
import com.taskmaster.dto.request.RegisterRequest;
import com.taskmaster.dto.request.LoginRequest;
import com.taskmaster.dto.response.AuthResponse;
import com.taskmaster.dto.response.RegisterResponse;
import com.taskmaster.exception.DuplicateResourceException;
import com.taskmaster.repository.UserRepository;
import com.taskmaster.security.JwtService;
import com.taskmaster.service.AuthService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registersUserWithNormalizedEmailAndEncodedPassword() {
        RegisterRequest request = new RegisterRequest(" John Doe ", " JOHN@EXAMPLE.COM ", "Password123");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123")).thenReturn("bcrypt-hash");
        User savedUser = new User("John Doe", "john@example.com", "bcrypt-hash");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegisterResponse response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("john@example.com");
        assertThat(captor.getValue().getPassword()).isEqualTo("bcrypt-hash");
        assertThat(captor.getValue().getPassword()).isNotEqualTo("Password123");
        assertThat(response.email()).isEqualTo("john@example.com");
    }

    @Test
    void rejectsDuplicateEmailBeforeSaving() {
        RegisterRequest request = new RegisterRequest("John Doe", "john@example.com", "Password123");
        when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(new User("Existing", "john@example.com", "hash")));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticatesNormalizedEmailAndReturnsJwt() {
        LoginRequest request = new LoginRequest(" JOHN@EXAMPLE.COM ", "Password123");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getName()).thenReturn("john@example.com");
        when(jwtService.generateToken("john@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("john@example.com", "Password123"));
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }
}
