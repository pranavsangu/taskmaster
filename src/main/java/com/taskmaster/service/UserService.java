package com.taskmaster.service;

import java.util.Locale;

import com.taskmaster.domain.User;
import com.taskmaster.dto.request.UpdateUserRequest;
import com.taskmaster.dto.response.UserResponse;
import com.taskmaster.exception.ResourceNotFoundException;
import com.taskmaster.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Authentication authentication) {
        return toResponse(findByAuthenticatedEmail(authentication));
    }

    @Transactional
    public UserResponse updateCurrentUser(Authentication authentication, UpdateUserRequest request) {
        User user = findByAuthenticatedEmail(authentication);
        user.setName(request.name().trim());
        return toResponse(userRepository.save(user));
    }

    private User findByAuthenticatedEmail(Authentication authentication) {
        String email = authentication.getName().trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
