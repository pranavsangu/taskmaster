package com.taskmaster;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.taskmaster.domain.User;
import com.taskmaster.repository.UserRepository;
import com.taskmaster.security.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void rejectsAnonymousProtectedRequests() throws Exception {
        mockMvc.perform(get("/api/protected-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsPublicRegistrationEndpoint() throws Exception {
        mockMvc.perform(get("/api/auth/register"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void allowsAuthenticatedRequestsThroughSecurityChain() throws Exception {
        mockMvc.perform(get("/api/protected-test").with(user("user@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void validJwtAuthenticatesPersistedUser() throws Exception {
        User savedUser = userRepository.save(
                new User("John Doe", "jwt-user@example.com", "bcrypt-hash"));
        String token = jwtService.generateToken(savedUser.getEmail());

        mockMvc.perform(get("/api/protected-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidJwtIsRejected() throws Exception {
        mockMvc.perform(get("/api/protected-test")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesOpenApiDocumentationWithoutJwt() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/projects']").exists())
                .andExpect(jsonPath("$.paths['/api/tasks/{taskId}/comments']").exists())
                .andExpect(jsonPath("$.paths['/api/tasks/{taskId}/attachments']").exists());
    }

    @Test
    void exposesSwaggerUiWithoutJwt() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
