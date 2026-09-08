package com.taskmaster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskmaster.controller.AttachmentController;
import com.taskmaster.dto.response.AttachmentResponse;
import com.taskmaster.exception.GlobalExceptionHandler;
import com.taskmaster.security.JwtService;
import com.taskmaster.service.AttachmentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttachmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AttachmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AttachmentService attachmentService;
    @MockBean private JwtService jwtService;

    @Test
    void uploadsAndListsMetadata() throws Exception {
        when(attachmentService.upload(any(), any(), any()))
                .thenReturn(new AttachmentResponse(1L, "file.txt", "text/plain", 5, 1L, null));
        mockMvc.perform(multipart("/api/tasks/5/attachments")
                        .file("file", "hello".getBytes()).with(user("user@example.com")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.originalFileName").value("file.txt"));
    }

    @Test
    void downloadsAndDeletesAttachment() throws Exception {
        when(attachmentService.download(any(), any()))
                .thenReturn(ResponseEntity.ok(new ByteArrayResource("hello".getBytes())));
        mockMvc.perform(get("/api/attachments/1/download").with(user("user@example.com")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/attachments/1").with(user("user@example.com")))
                .andExpect(status().isNoContent());
    }
}
