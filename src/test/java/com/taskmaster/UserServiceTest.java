package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.taskmaster.domain.User;
import com.taskmaster.dto.request.UpdateUserRequest;
import com.taskmaster.dto.response.UserResponse;
import com.taskmaster.repository.UserRepository;
import com.taskmaster.service.UserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserService userService;

    @Test
    void getsProfileForAuthenticatedUserOnly() {
        User user = new User("John Doe", "john@example.com", "hash");
        when(authentication.getName()).thenReturn(" JOHN@EXAMPLE.COM ");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));

        UserResponse response = userService.getCurrentUser(authentication);

        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.name()).isEqualTo("John Doe");
        verify(userRepository).findByEmail("john@example.com");
    }

    @Test
    void updatesOnlyAuthenticatedUsersName() {
        User user = new User("John Doe", "john@example.com", "hash");
        when(authentication.getName()).thenReturn("john@example.com");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateCurrentUser(
                authentication,
                new UpdateUserRequest(" John Updated "));

        assertThat(user.getName()).isEqualTo("John Updated");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(user.getPassword()).isEqualTo("hash");
    }
}
