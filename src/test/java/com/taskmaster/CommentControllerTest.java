package com.taskmaster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmaster.controller.CommentController;
import com.taskmaster.dto.request.CreateCommentRequest;
import com.taskmaster.dto.request.UpdateCommentRequest;
import com.taskmaster.dto.response.CommentResponse;
import com.taskmaster.exception.GlobalExceptionHandler;
import com.taskmaster.security.JwtService;
import com.taskmaster.service.CommentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CommentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private CommentService commentService;
    @MockBean private JwtService jwtService;

    @Test
    void createsComment() throws Exception {
        when(commentService.create(any(), any(), any(CreateCommentRequest.class)))
                .thenReturn(new CommentResponse(1L, 5L, 2L, "User", "hello", null, null));

        mockMvc.perform(post("/api/tasks/5/comments").with(user("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("hello"));
    }

    @Test
    void rejectsBlankComment() throws Exception {
        mockMvc.perform(post("/api/tasks/5/comments").with(user("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    void updatesAndDeletesComment() throws Exception {
        when(commentService.update(any(), any(), any(UpdateCommentRequest.class)))
                .thenReturn(new CommentResponse(1L, 5L, 2L, "User", "updated", null, null));

        mockMvc.perform(put("/api/comments/1").with(user("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("updated"));

        mockMvc.perform(delete("/api/comments/1").with(user("user@example.com")))
                .andExpect(status().isNoContent());
    }
}
