package com.trustdesk.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.draft.DraftReplyRepository;
import com.trustdesk.triage.TriageResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every triage and draft run leaves a trace that can be fetched by its run_id (mock AI, seeded database). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"trustdesk.demo-token=test-token", "trustdesk.ai.provider=mock"})
class AgentRunControllerTest {

    private static final String AUTH = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private DraftReplyRepository draftReplyRepository;

    @Autowired
    private TriageResultRepository triageResultRepository;

    private final OffsetDateTime testStart = OffsetDateTime.now();

    @AfterEach
    void deleteRowsCreatedByTest() {
        agentRunRepository.deleteAll(agentRunRepository.findAll().stream()
                .filter(r -> !r.getCreatedAt().isBefore(testStart)).toList());
        draftReplyRepository.deleteAll(draftReplyRepository.findAll().stream()
                .filter(d -> !d.getCreatedAt().isBefore(testStart)).toList());
        triageResultRepository.deleteAll(triageResultRepository.findAll().stream()
                .filter(t -> !t.getCreatedAt().isBefore(testStart)).toList());
    }

    private JsonNode postJson(String path) throws Exception {
        String body = mockMvc.perform(post(path).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void triageRunIsTraced() throws Exception {
        JsonNode triage = postJson("/tickets/tkt_9001/triage");
        String runId = triage.get("run_id").asText();
        assertThat(runId).startsWith("run_");

        mockMvc.perform(get("/agent-runs/" + runId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_id").value(runId))
                .andExpect(jsonPath("$.ticket_id").value("tkt_9001"))
                .andExpect(jsonPath("$.run_type").value("triage"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.result_id").value(triage.get("triage_id").asText()))
                .andExpect(jsonPath("$.retrieved_doc_ids[0]").value(triage.get("retrieved_doc_ids").get(0).asText()))
                .andExpect(jsonPath("$.guardrail_results.status").value("passed"))
                .andExpect(jsonPath("$.model_provider").value("mock"))
                .andExpect(jsonPath("$.model_name").value("keyword-rules-v1"))
                .andExpect(jsonPath("$.prompt_version").value("mock-templates-v1"))
                .andExpect(jsonPath("$.latency_ms", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.created_at", org.hamcrest.Matchers.endsWith("+05:30")));
    }

    @Test
    void draftRunTraceShowsToolsAndGuardrails() throws Exception {
        postJson("/tickets/tkt_9006/triage");
        JsonNode draft = postJson("/tickets/tkt_9006/draft-reply");
        String runId = draft.get("run_id").asText();

        mockMvc.perform(get("/agent-runs/" + runId).header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_type").value("draft_reply"))
                .andExpect(jsonPath("$.status").value("escalated"))
                .andExpect(jsonPath("$.result_id").value(draft.get("draft_id").asText()))
                .andExpect(jsonPath("$.tool_calls[0].tool_name").value("escalate_to_human"))
                .andExpect(jsonPath("$.tool_calls[0].outcome").value("recommended"))
                .andExpect(jsonPath("$.guardrail_results.status").value("flagged"))
                .andExpect(jsonPath("$.guardrail_results.findings[*].type", hasItem("prompt_injection")));

        // The stored draft keeps pointing at its trace.
        mockMvc.perform(get("/tickets/tkt_9006/draft-reply").header("Authorization", AUTH))
                .andExpect(jsonPath("$.run_id").value(runId));
    }

    @Test
    void ticketRunsAreListedOldestFirst() throws Exception {
        String triageRun = postJson("/tickets/tkt_9002/triage").get("run_id").asText();
        String draftRun = postJson("/tickets/tkt_9002/draft-reply").get("run_id").asText();

        String body = mockMvc.perform(get("/tickets/tkt_9002/agent-runs").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode runs = objectMapper.readTree(body);
        java.util.List<String> ids = new java.util.ArrayList<>();
        runs.forEach(r -> ids.add(r.get("run_id").asText()));
        assertThat(ids).containsSubsequence(triageRun, draftRun);
    }

    @Test
    void unknownRunAndTicketReturn404AndTokenIsRequired() throws Exception {
        mockMvc.perform(get("/agent-runs/run_missing").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", startsWith("Agent run run_missing")));
        mockMvc.perform(get("/tickets/tkt_0000/agent-runs").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/agent-runs/run_x")).andExpect(status().isUnauthorized());
    }
}
