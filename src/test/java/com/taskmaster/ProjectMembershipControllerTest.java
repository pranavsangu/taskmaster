package com.taskmaster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmaster.controller.ProjectMembershipController;
import com.taskmaster.dto.request.AddMemberRequest;
import com.taskmaster.dto.response.ProjectMemberResponse;
import com.taskmaster.enums.ProjectRole;
import com.taskmaster.exception.GlobalExceptionHandler;
import com.taskmaster.security.JwtService;
import com.taskmaster.service.ProjectMembershipService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectMembershipController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProjectMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectMembershipService membershipService;

    @MockBean
    private JwtService jwtService;

    @Test
    void addsMemberWithValidRequest() throws Exception {
        when(membershipService.addMember(any(), any(), any(AddMemberRequest.class)))
                .thenReturn(new ProjectMemberResponse(
                        1L, 2L, "Member", "member@example.com", ProjectRole.MEMBER, null));

        mockMvc.perform(post("/api/projects/1/members")
                        .with(user("owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("member@example.com"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void validatesMemberEmail() throws Exception {
        mockMvc.perform(post("/api/projects/1/members")
                        .with(user("owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }
}
