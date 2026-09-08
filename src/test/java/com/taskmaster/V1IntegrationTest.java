package com.taskmaster;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class V1IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void cleanAttachmentFiles() throws Exception {
        Path directory = Paths.get(System.getProperty("java.io.tmpdir"), "taskmaster-test-uploads");
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }

    @Test
    void completesAuthenticatedProjectTaskCommentAndAttachmentFlow() throws Exception {
        String ownerEmail = uniqueEmail("owner");
        String memberEmail = uniqueEmail("member");
        register(ownerEmail);
        register(memberEmail);
        String ownerToken = login(ownerEmail);
        String memberToken = login(memberEmail);

        long projectId = createProject(ownerToken);
        mockMvc.perform(post("/api/projects/{projectId}/members", projectId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + memberEmail + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        MvcResult taskResult = mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Integration task\"}"))
                .andExpect(status().isCreated()).andReturn();
        long taskId = body(taskResult).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/{taskId}/status", taskId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        MvcResult commentResult = mockMvc.perform(post("/api/tasks/{taskId}/comments", taskId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Working on it\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("Working on it"))
                .andReturn();
        long commentId = body(commentResult).get("id").asLong();

        mockMvc.perform(get("/api/tasks/{taskId}/comments", taskId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(commentId));

        MvcResult attachmentResult = mockMvc.perform(multipart("/api/tasks/{taskId}/attachments", taskId)
                        .file(new MockMultipartFile("file", "file.txt", "text/plain",
                                "integration content".getBytes()))
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFileName").value("file.txt"))
                .andReturn();
        long attachmentId = body(attachmentResult).get("id").asLong();

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/attachments/{attachmentId}", attachmentId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/comments/{commentId}", commentId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Updated\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void nonMemberCannotReadProject() throws Exception {
        String ownerEmail = uniqueEmail("owner");
        String outsiderEmail = uniqueEmail("outsider");
        register(ownerEmail);
        register(outsiderEmail);
        long projectId = createProject(login(ownerEmail));

        mockMvc.perform(get("/api/projects/{projectId}", projectId)
                        .header("Authorization", bearer(login(outsiderEmail))))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberCannotUpdateProject() throws Exception {
        String ownerEmail = uniqueEmail("owner");
        String memberEmail = uniqueEmail("member");
        register(ownerEmail);
        register(memberEmail);
        String ownerToken = login(ownerEmail);
        long projectId = createProject(ownerToken);
        mockMvc.perform(post("/api/projects/{projectId}/members", projectId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + memberEmail + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/projects/{projectId}", projectId)
                        .header("Authorization", bearer(login(memberEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changed\"}"))
                .andExpect(status().isForbidden());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Integration User\",\"email\":\"" + email
                                + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("token").asText();
    }

    private long createProject(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Integration project\"}"))
                .andExpect(status().isCreated()).andReturn();
        return body(result).get("id").asLong();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) { return "Bearer " + token; }
    private String uniqueEmail(String prefix) { return prefix + "-" + UUID.randomUUID() + "@example.com"; }
}
