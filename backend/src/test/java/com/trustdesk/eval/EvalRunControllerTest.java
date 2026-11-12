package com.trustdesk.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.draft.DraftReplyRepository;
import com.trustdesk.trace.AgentRunRepository;
import com.trustdesk.triage.TriageResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A real eval run in the background with the mock AI, against the seeded eval cases. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"trustdesk.demo-token=test-token", "trustdesk.ai.provider=mock"})
class EvalRunControllerTest {

    private static final String AUTH = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EvalCaseRepository evalCaseRepository;

    @Autowired
    private EvalRunRepository evalRunRepository;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private DraftReplyRepository draftReplyRepository;

    @Autowired
    private TriageResultRepository triageResultRepository;

    private final OffsetDateTime testStart = OffsetDateTime.now();

    @AfterEach
    void deleteRowsCreatedByTest() {
        evalRunRepository.deleteAll(evalRunRepository.findAll().stream()
                .filter(r -> !r.getQueuedAt().isBefore(testStart)).toList());
        agentRunRepository.deleteAll(agentRunRepository.findAll().stream()
                .filter(r -> !r.getCreatedAt().isBefore(testStart)).toList());
        draftReplyRepository.deleteAll(draftReplyRepository.findAll().stream()
                .filter(d -> !d.getCreatedAt().isBefore(testStart)).toList());
        triageResultRepository.deleteAll(triageResultRepository.findAll().stream()
                .filter(t -> !t.getCreatedAt().isBefore(testStart)).toList());
    }

    private JsonNode start(String body) throws Exception {
        var request = post("/eval-runs").header("Authorization", AUTH);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String response = mockMvc.perform(request)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("running"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    /** Polls until the background run has finished (at most 60 seconds). */
    private JsonNode waitForCompletion(String evalRunId) throws Exception {
        for (int i = 0; i < 120; i++) {
            String body = mockMvc.perform(get("/eval-runs/" + evalRunId).header("Authorization", AUTH))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode run = objectMapper.readTree(body);
            if (!run.get("status").asText().equals("running")) {
                return run;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Eval run " + evalRunId + " did not finish within 60 seconds");
    }

    @Test
    void seedLoaderLoadedAllEightEvalCases() {
        assertThat(evalCaseRepository.findAllByOrderByCaseIdAsc()).extracting(EvalCase::getCaseId)
                .containsExactly("eval_001", "eval_002", "eval_003", "eval_004", "eval_005", "eval_006", "eval_007", "eval_008");
    }

    @Test
    void fullRunProducesMetricsCaseResultsAndAdversarialVerdicts() throws Exception {
        JsonNode started = start(null);
        assertThat(started.get("eval_run_id").asText()).startsWith("eval_run_");
        assertThat(started.get("total_cases").asInt()).isEqualTo(8);

        JsonNode run = waitForCompletion(started.get("eval_run_id").asText());

        assertThat(run.get("status").asText()).isEqualTo("completed");
        assertThat(run.get("started_at").isNull()).isFalse();
        assertThat(run.get("completed_cases").asInt()).isEqualTo(8);
        for (String metric : new String[] {"triage_accuracy", "category_accuracy", "priority_accuracy",
                "citation_coverage", "unsafe_action_block_rate", "allowed_action_recall", "escalation_accuracy",
                "answer_requirement_coverage", "pass_rate"}) {
            assertThat(run.get(metric).isNumber()).as(metric).isTrue();
        }
        // Safety must hold even with the simple mock AI: Java validation and guardrails do the work.
        assertThat(run.get("unsafe_action_block_rate").asDouble()).isEqualTo(1.0);
        assertThat(run.get("escalation_accuracy").asDouble()).isEqualTo(1.0);

        JsonNode adversarial = run.get("adversarial_checks");
        assertThat(adversarial).hasSize(3);
        adversarial.forEach(c -> assertThat(c.get("safe").asBoolean()).as(c.get("case_id").asText()).isTrue());

        JsonNode first = run.get("case_results").get(0);
        assertThat(first.get("case_id").asText()).isEqualTo("eval_001");
        assertThat(first.get("triage_run_id").asText()).startsWith("run_");
        assertThat(first.get("draft_run_id").asText()).startsWith("run_");
        assertThat(first.get("answer_checks")).hasSize(3);

        // One eval_case trace per case, pointing at this eval run.
        assertThat(agentRunRepository.findAll().stream()
                .filter(r -> r.getRunType().equals("eval_case")
                        && run.get("eval_run_id").asText().equals(r.getResultId()))).hasSize(8);
    }

    @Test
    void selectedCasesOnly() throws Exception {
        JsonNode started = start("{\"case_ids\": [\"eval_006\", \"eval_007\"]}");
        assertThat(started.get("total_cases").asInt()).isEqualTo(2);

        JsonNode run = waitForCompletion(started.get("eval_run_id").asText());

        assertThat(run.get("case_results")).extracting(r -> r.get("case_id").asText())
                .containsExactly("eval_006", "eval_007");
        assertThat(run.get("adversarial_checks")).hasSize(2);
    }

    @Test
    void unknownCaseIdIsRejected() throws Exception {
        mockMvc.perform(post("/eval-runs").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"case_ids\": [\"eval_999\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown case_ids: eval_999"));
    }

    @Test
    void unknownRunReturns404AndTokenIsRequired() throws Exception {
        mockMvc.perform(get("/eval-runs/eval_run_missing").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/eval-runs")).andExpect(status().isUnauthorized());
    }
}
