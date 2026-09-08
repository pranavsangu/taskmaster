package com.taskmaster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmaster.controller.TaskController;
import com.taskmaster.dto.request.CreateTaskRequest;
import com.taskmaster.dto.response.PageResponse;
import com.taskmaster.dto.response.TaskResponse;
import com.taskmaster.enums.TaskStatus;
import com.taskmaster.exception.GlobalExceptionHandler;
import com.taskmaster.security.JwtService;
import com.taskmaster.service.TaskService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private JwtService jwtService;

    @Test
    void createsTaskWithValidRequest() throws Exception {
        when(taskService.create(any(), any(), any(CreateTaskRequest.class)))
                .thenReturn(new TaskResponse(
                        1L, "Task", null, null, TaskStatus.TODO,
                        10L, 1L, null, null, null, null));

        mockMvc.perform(post("/api/projects/10/tasks")
                        .with(user("creator@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Task\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void rejectsBlankTaskTitle() throws Exception {
        mockMvc.perform(post("/api/projects/10/tasks")
                        .with(user("creator@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void updatesTaskStatus() throws Exception {
        when(taskService.updateStatus(any(), any(), any()))
                .thenReturn(new TaskResponse(
                        1L, "Task", null, null, TaskStatus.COMPLETED,
                        10L, 1L, null, null, null, null));

        mockMvc.perform(patch("/api/tasks/1/status")
                        .with(user("creator@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void rejectsNullTaskStatus() throws Exception {
        mockMvc.perform(patch("/api/tasks/1/status")
                        .with(user("creator@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void returnsPagedProjectTasks() throws Exception {
        when(taskService.search(any(), any(), any()))
                .thenReturn(new PageResponse<>(java.util.List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/projects/10/tasks")
                        .with(user("creator@example.com"))
                        .param("status", "TODO")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
