package com.trustdesk.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The demo page loads without a token; everything else still needs one. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "trustdesk.demo-token=test-token")
class FrontendPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pageFilesLoadWithoutToken() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mockMvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(containsString("TrustDesk")));
        mockMvc.perform(get("/app.js")).andExpect(status().isOk());
        mockMvc.perform(get("/styles.css")).andExpect(status().isOk());
    }

    @Test
    void apiStillNeedsToken() throws Exception {
        mockMvc.perform(get("/tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/eval-runs")).andExpect(status().isUnauthorized());
        // Only GET/HEAD of the exact page files is public.
        mockMvc.perform(post("/index.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/other.html")).andExpect(status().isUnauthorized());
    }

    @Test
    void pageScriptNeverUsesInnerHtml() throws Exception {
        // Ticket text and AI replies are untrusted; the page must insert them as text only.
        String script = Files.readString(Path.of("../frontend/app.js"));
        assertThat(script).doesNotContain("innerHTML").doesNotContain("outerHTML").doesNotContain("insertAdjacentHTML");
    }
}
