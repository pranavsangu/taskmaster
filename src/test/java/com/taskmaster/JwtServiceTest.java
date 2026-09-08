package com.taskmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import com.taskmaster.security.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "a-test-secret-that-is-at-least-32-bytes".getBytes());

    @Test
    void generatesAndValidatesTokenForUser() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        User user = (User) User.withUsername("john@example.com")
                .password("hash")
                .authorities("USER")
                .build();

        String token = jwtService.generateToken(user.getUsername());

        assertThat(jwtService.extractUsername(token)).isEqualTo("john@example.com");
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwtService = new JwtService(SECRET, -1);
        String token = jwtService.generateToken("john@example.com");
        User user = (User) User.withUsername("john@example.com")
                .password("hash")
                .authorities("USER")
                .build();

        assertThatThrownBy(() -> jwtService.isTokenValid(token, user))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }
}
