package com.trustdesk.trace;

import com.trustdesk.ai.AiProvider;
import com.trustdesk.ai.AiProviderException;
import com.trustdesk.ai.DraftSuggestion;
import com.trustdesk.ai.DraftSuggestion.ActionSuggestion;
import com.trustdesk.ai.TriageSuggestion;
import com.trustdesk.draft.DraftReplyRepository;
import com.trustdesk.triage.TriageResultRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Traces of failed runs, and of tools that validation removed, using a fake AI. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "trustdesk.demo-token=test-token")
class AgentRunFailureTest {

    private static final String AUTH = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private DraftReplyRepository draftReplyRepository;

    @Autowired
    private TriageResultRepository triageResultRepository;

    @MockitoBean
    private AiProvider aiProvider;

    private final OffsetDateTime testStart = OffsetDateTime.now();

    @BeforeEach
    void fakeProvider() {
        when(aiProvider.name()).thenReturn("fake");
        when(aiProvider.model()).thenReturn("fake-model");
        when(aiProvider.promptVersion()).thenReturn("fake-prompts-v1");
    }

    @AfterEach
    void deleteRowsCreatedByTest() {
        agentRunRepository.deleteAll(runsSinceTestStart());
        draftReplyRepository.deleteAll(draftReplyRepository.findAll().stream()
                .filter(d -> !d.getCreatedAt().isBefore(testStart)).toList());
        triageResultRepository.deleteAll(triageResultRepository.findAll().stream()
                .filter(t -> !t.getCreatedAt().isBefore(testStart)).toList());
    }

    private List<AgentRun> runsSinceTestStart() {
        return agentRunRepository.findAll().stream().filter(r -> !r.getCreatedAt().isBefore(testStart)).toList();
    }

    @Test
    void failedAiCallIsStillTraced() throws Exception {
        when(aiProvider.triage(any())).thenThrow(new AiProviderException("Groq returned HTTP 429"));

        mockMvc.perform(post("/tickets/tkt_9001/triage").header("Authorization", AUTH))
                .andExpect(status().isBadGateway());

        // The triage was rolled back, but its trace was saved in its own transaction.
        assertThat(triageResultRepository.findAll().stream().filter(t -> !t.getCreatedAt().isBefore(testStart))).isEmpty();
        List<AgentRun> runs = runsSinceTestStart();
        assertThat(runs).hasSize(1);
        AgentRun run = runs.get(0);
        assertThat(run.getRunType()).isEqualTo("triage");
        assertThat(run.getStatus()).isEqualTo("failed");
        assertThat(run.getError()).isEqualTo("Groq returned HTTP 429");
        assertThat(run.getResultId()).isNull();
        assertThat(run.getRetrievedDocIds()).contains("KB-REFUND-001");
    }

    @Test
    void invalidAiOutputIsTracedAsFailed() throws Exception {
        when(aiProvider.triage(any())).thenReturn(new TriageSuggestion("approve_all", "medium", "neutral", false, "x"));

        mockMvc.perform(post("/tickets/tkt_9001/triage").header("Authorization", AUTH))
                .andExpect(status().isBadGateway());

        assertThat(runsSinceTestStart()).singleElement()
                .satisfies(r -> assertThat(r.getError()).isEqualTo("AI returned an invalid category: 'approve_all'"));
    }

    @Test
    void removedToolsAppearInTheDraftTrace() throws Exception {
        when(aiProvider.triage(any())).thenReturn(new TriageSuggestion("refund", "medium", "frustrated", false, "x"));
        when(aiProvider.draft(any())).thenReturn(new DraftSuggestion("We will arrange a replacement after review.",
                List.of("KB-REFUND-001"),
                List.of(new ActionSuggestion("create_replacement_order", "Damaged."),
                        new ActionSuggestion("issue_coupon", "Goodwill.")),
                false, null));

        mockMvc.perform(post("/tickets/tkt_9001/triage").header("Authorization", AUTH)).andExpect(status().isOk());
        mockMvc.perform(post("/tickets/tkt_9001/draft-reply").header("Authorization", AUTH)).andExpect(status().isOk());

        AgentRun draftRun = runsSinceTestStart().stream().filter(r -> r.getRunType().equals("draft_reply")).findFirst()
                .orElseThrow();
        assertThat(draftRun.getToolCalls()).containsExactly(
                new ToolCall("create_replacement_order", "recommended", true, "Damaged."),
                new ToolCall("issue_coupon", "removed", null,
                        "Removed action 'issue_coupon': not an allowed tool for category 'refund'."));
        assertThat(draftRun.getPromptVersion()).isEqualTo("fake-prompts-v1");
    }
}
