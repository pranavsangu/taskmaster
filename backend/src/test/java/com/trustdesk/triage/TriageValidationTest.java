package com.trustdesk.triage;

import com.trustdesk.ai.AiProvider;
import com.trustdesk.ai.TriageSuggestion;
import com.trustdesk.trace.AgentRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "AI recommends, code decides": replaces the AI provider with a fake that returns chosen
 * (sometimes bad) answers, and checks what the triage service does with them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "trustdesk.demo-token=test-token")
class TriageValidationTest {

    private static final String AUTH = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private TriageResultRepository triageResultRepository;

    @MockitoBean
    private AiProvider aiProvider;

    private final OffsetDateTime testStart = OffsetDateTime.now();

    @BeforeEach
    void namesTheFakeProvider() {
        when(aiProvider.name()).thenReturn("fake");
        when(aiProvider.model()).thenReturn("fake-model");
        when(aiProvider.promptVersion()).thenReturn("fake-prompts-v1");
    }

    @AfterEach
    void deleteAgentRunsCreatedByTest() {
        agentRunRepository.deleteAll(agentRunRepository.findAll().stream()
                .filter(r -> !r.getCreatedAt().isBefore(testStart)).toList());
    }

    @AfterEach
    void deleteTriagesCreatedByTest() {
        triageResultRepository.deleteAll(triageResultRepository.findAll().stream()
                .filter(t -> !t.getCreatedAt().isBefore(testStart))
                .toList());
    }

    private long storedSinceTestStart() {
        return triageResultRepository.findAll().stream().filter(t -> !t.getCreatedAt().isBefore(testStart)).count();
    }

    @Test
    void invalidCategoryIsRejectedAndNothingStored() throws Exception {
        when(aiProvider.triage(any())).thenReturn(
                new TriageSuggestion("approve_all_refunds", "medium", "neutral", false, "x"));

        mockMvc.perform(post("/tickets/tkt_9001/triage").header("Authorization", AUTH))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("ai_provider_error"))
                .andExpect(jsonPath("$.message").value("AI returned an invalid category: 'approve_all_refunds'"));

        assertThat(storedSinceTestStart()).isZero();
    }

    @Test
    void invalidPriorityIsRejected() throws Exception {
        when(aiProvider.triage(any())).thenReturn(
                new TriageSuggestion("refund", "critical!!", "neutral", false, "x"));

        mockMvc.perform(post("/tickets/tkt_9001/triage").header("Authorization", AUTH))
                .andExpect(status().isBadGateway());

        assertThat(storedSinceTestStart()).isZero();
    }

    @Test
    void valuesAreNormalisedAndUnknownSentimentTolerated() throws Exception {
        when(aiProvider.triage(any())).thenReturn(
                new TriageSuggestion("  Refund ", "MEDIUM", "ecstatic", false, "  Damaged\n item.  "));

        mockMvc.perform(post("/tickets/tkt_9001/triage").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("refund"))
                .andExpect(jsonPath("$.priority").value("medium"))
                .andExpect(jsonPath("$.sentiment").value("unknown"))
                .andExpect(jsonPath("$.reason_summary").value("Damaged item."));
    }

    @Test
    void urgentPriorityForcesEscalationEvenIfAiSaysNo() throws Exception {
        when(aiProvider.triage(any())).thenReturn(
                new TriageSuggestion("warranty", "urgent", "worried", false, "Battery swelling."));

        mockMvc.perform(post("/tickets/tkt_9004/triage").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.should_escalate").value(true));
    }
}