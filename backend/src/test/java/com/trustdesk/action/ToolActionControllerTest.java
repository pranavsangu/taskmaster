package com.trustdesk.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.triage.TriageResultRepository;
import com.trustdesk.trace.AgentRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The approval-gated action workflow against the seeded database, with the mock AI for triage.
 * Every test uses fresh idempotency keys and deletes the rows it created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"trustdesk.demo-token=test-token", "trustdesk.ai.provider=mock"})
class ToolActionControllerTest {

    private static final String AUTH = "Bearer test-token";
    private static final Map<String, String> REVIEW = Map.of("reviewer_id", "manager_priya", "reason", "Photo checked.");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ToolActionRequestRepository actionRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private TriageResultRepository triageResultRepository;

    private final OffsetDateTime testStart = OffsetDateTime.now();
    private final String keyPrefix = "test-" + UUID.randomUUID() + "-";

    @AfterEach
    void deleteAgentRunsCreatedByTest() {
        agentRunRepository.deleteAll(agentRunRepository.findAll().stream()
                .filter(r -> !r.getCreatedAt().isBefore(testStart)).toList());
    }

    @AfterEach
    void deleteRowsCreatedByTest() {
        approvalRepository.deleteAll(approvalRepository.findAll().stream()
                .filter(a -> !a.getCreatedAt().isBefore(testStart)).toList());
        actionRepository.deleteAll(actionRepository.findAll().stream()
                .filter(a -> a.getIdempotencyKey().startsWith(keyPrefix)).toList());
        triageResultRepository.deleteAll(triageResultRepository.findAll().stream()
                .filter(t -> !t.getCreatedAt().isBefore(testStart)).toList());
    }

    // ---- helpers ----

    private void triage(String ticketId) throws Exception {
        mockMvc.perform(post("/tickets/" + ticketId + "/triage").header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    private Map<String, Object> replacementPayload(String key) {
        return Map.of("order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "Damaged on arrival",
                "idempotency_key", keyPrefix + key);
    }

    private ResultActions requestAction(String ticketId, String tool, Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("ticket_id", ticketId, "tool_name", tool, "payload", payload));
        return mockMvc.perform(post("/tool-actions").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String actionId(ResultActions result) throws Exception {
        JsonNode json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return json.get("action_id").asText();
    }

    private ResultActions approve(String actionId, Map<String, String> review) throws Exception {
        return mockMvc.perform(post("/tool-actions/" + actionId + "/approve").header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(review)));
    }

    private ResultActions execute(String actionId) throws Exception {
        return mockMvc.perform(post("/tool-actions/" + actionId + "/execute").header("Authorization", AUTH));
    }

    // ---- the approval gate ----

    @Test
    void sensitiveActionNeedsApprovalBeforeExecution() throws Exception {
        triage("tkt_9001");
        String id = actionId(requestAction("tkt_9001", "create_replacement_order", replacementPayload("gate"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.action_id", startsWith("act_")))
                .andExpect(jsonPath("$.status").value("approval_required"))
                .andExpect(jsonPath("$.requires_human_approval").value(true))
                .andExpect(jsonPath("$.risk_level").value("medium"))
                .andExpect(jsonPath("$.requested_by").value("support_agent"))
                .andExpect(jsonPath("$.idempotent_replay").value(false)));

        execute(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("requires human approval")));
        mockMvc.perform(get("/tool-actions/" + id).header("Authorization", AUTH))
                .andExpect(jsonPath("$.status").value("approval_required"))
                .andExpect(jsonPath("$.result").value(nullValue()));

        approve(id, REVIEW)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.approvals", hasSize(1)))
                .andExpect(jsonPath("$.approvals[0].reviewer_id").value("manager_priya"))
                .andExpect(jsonPath("$.approvals[0].decision").value("approved"));

        String firstResult = execute(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("executed"))
                .andExpect(jsonPath("$.result.replacement_order_id", startsWith("rpl_")))
                .andExpect(jsonPath("$.result.simulated").value(true))
                .andReturn().getResponse().getContentAsString();

        // Executing again does not run the tool a second time: same result, marked as a replay.
        // (Read back from the database, whose timestamps are UTC: still shown as +05:30.)
        String secondResult = execute(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent_replay").value(true))
                .andExpect(jsonPath("$.executed_at", org.hamcrest.Matchers.endsWith("+05:30")))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(secondResult).get("result"))
                .isEqualTo(objectMapper.readTree(firstResult).get("result"));
    }

    @Test
    void rejectedActionCanNeverRun() throws Exception {
        triage("tkt_9001");
        String id = actionId(requestAction("tkt_9001", "create_replacement_order", replacementPayload("reject")));

        mockMvc.perform(post("/tool-actions/" + id + "/reject").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reviewer_id", "manager_priya", "reason", "No photo."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.approvals[0].decision").value("rejected"));

        execute(id).andExpect(status().isConflict());
        approve(id, REVIEW).andExpect(status().isConflict());
    }

    @Test
    void reviewerMustBeHumanAndNotTheRequester() throws Exception {
        triage("tkt_9001");
        String id = actionId(requestAction("tkt_9001", "create_replacement_order", replacementPayload("reviewer")));

        approve(id, Map.of("reviewer_id", "ai_agent", "reason", "Looks fine."))
                .andExpect(status().isBadRequest());
        approve(id, Map.of("reviewer_id", "support_agent", "reason", "Approving my own request."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("different person")));
        approve(id, Map.of("reviewer_id", "manager_priya"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("reason is required"));
    }

    @Test
    void lowRiskToolNeedsNoApproval() throws Exception {
        triage("tkt_9002");
        String id = actionId(requestAction("tkt_9002", "open_carrier_investigation", Map.of(
                "order_id", "ord_5002", "tracking_number", "BLUETRK10002", "reason", "No movement for 6 days",
                "idempotency_key", keyPrefix + "carrier"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requires_human_approval").value(false))
                .andExpect(jsonPath("$.status").value("approved")));

        execute(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("executed"));
    }

    // ---- idempotency ----

    @Test
    void sameIdempotencyKeyTwiceCreatesOneAction() throws Exception {
        triage("tkt_9001");
        String first = actionId(requestAction("tkt_9001", "create_replacement_order", replacementPayload("twice"))
                .andExpect(status().isCreated()));
        String second = actionId(requestAction("tkt_9001", "create_replacement_order", replacementPayload("twice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotent_replay").value(true)));

        assertThat(second).isEqualTo(first);
        assertThat(actionRepository.findAll().stream()
                .filter(a -> a.getIdempotencyKey().equals(keyPrefix + "twice")).count()).isEqualTo(1);
    }

    @Test
    void sameKeyForDifferentRequestIsConflict() throws Exception {
        triage("tkt_9001");
        requestAction("tkt_9001", "create_replacement_order", replacementPayload("reuse")).andExpect(status().isCreated());

        Map<String, Object> different = Map.of("order_id", "ord_5001", "sku", "BG-AIRPODS-01",
                "reason", "Changed reason", "idempotency_key", keyPrefix + "reuse");
        requestAction("tkt_9001", "create_replacement_order", different)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already used for a different request")));
    }

    @Test
    void simultaneousIdenticalRequestsCreateOneAction() throws Exception {
        triage("tkt_9001");
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return requestAction("tkt_9001", "create_replacement_order", replacementPayload("race"))
                            .andReturn().getResponse().getStatus();
                };
                statuses.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : statuses) {
                codes.add(f.get());
            }
            assertThat(codes).containsOnly(201, 200);
            assertThat(codes.stream().filter(c -> c == 201).count()).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
        assertThat(actionRepository.findAll().stream()
                .filter(a -> a.getIdempotencyKey().equals(keyPrefix + "race")).count()).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        triage("tkt_9001");
        requestAction("tkt_9001", "create_replacement_order",
                Map.of("order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "Damaged"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("payload.idempotency_key is required")));
    }

    // ---- validation ----

    @Test
    void missingRequiredFieldsAreListed() throws Exception {
        triage("tkt_9001");
        requestAction("tkt_9001", "create_replacement_order", Map.of("order_id", "ord_5001",
                "idempotency_key", keyPrefix + "missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("payload.sku is required; payload.reason is required"));
    }

    @Test
    void unknownToolIsRejected() throws Exception {
        triage("tkt_9001");
        requestAction("tkt_9001", "approve_all_refunds", Map.of("idempotency_key", keyPrefix + "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown tool 'approve_all_refunds'"));
    }

    @Test
    void toolNotAllowedForCategoryIsRejected() throws Exception {
        triage("tkt_9001"); // refund
        requestAction("tkt_9001", "issue_coupon", Map.of("customer_id", "cus_1001", "amount", 500,
                "reason", "Goodwill", "idempotency_key", keyPrefix + "coupon"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("action_not_allowed"))
                .andExpect(jsonPath("$.message", containsString("not allowed for category 'refund'")));
    }

    @Test
    void payloadMustMatchTheTicketsOrder() throws Exception {
        triage("tkt_9001");
        requestAction("tkt_9001", "create_replacement_order", Map.of("order_id", "ord_5005", "sku", "BG-TAB-10",
                "reason", "Damaged", "idempotency_key", keyPrefix + "other-order"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("payload.order_id is not the ticket's order")))
                .andExpect(jsonPath("$.message", containsString("payload.sku is not an item of the ticket's order")));
    }

    @Test
    void refundForSoftwareLicenseIsBlockedByPolicy() throws Exception {
        triage("tkt_9003");
        requestAction("tkt_9003", "start_refund_review", Map.of("order_id", "ord_5004", "reason", "Changed mind",
                "amount", 4999, "idempotency_key", keyPrefix + "license"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("not supported by the return policy")));
    }

    @Test
    void refundAmountCannotExceedOrderTotal() throws Exception {
        triage("tkt_9008"); // billing
        requestAction("tkt_9008", "start_refund_review", Map.of("order_id", "ord_5006", "reason", "Duplicate charge",
                "amount", 99999, "idempotency_key", keyPrefix + "too-much"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("exceeds the order total")));
        requestAction("tkt_9008", "start_refund_review", Map.of("order_id", "ord_5006", "reason", "Duplicate charge",
                "amount", 15999, "idempotency_key", keyPrefix + "ok"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approval_required"));
    }

    @Test
    void escalatedTicketAllowsOnlyEscalateToHuman() throws Exception {
        triage("tkt_9006"); // prompt injection: general, escalated
        requestAction("tkt_9006", "issue_coupon", Map.of("customer_id", "cus_1006", "amount", 5000,
                "reason", "Customer asked", "idempotency_key", keyPrefix + "injection-coupon"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("only escalate_to_human may be requested")))
                .andExpect(jsonPath("$.message", containsString("exceeds the limit of INR 1000")));

        requestAction("tkt_9006", "escalate_to_human", Map.of("ticket_id", "tkt_9006", "reason", "Prompt injection",
                "queue", "trust_and_safety", "idempotency_key", keyPrefix + "injection-escalate"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approved"));
    }

    // ---- lookups and auth ----

    @Test
    void ticketActionsAreListedAndUnknownIdsReturn404() throws Exception {
        triage("tkt_9001");
        requestAction("tkt_9001", "create_replacement_order", replacementPayload("list")).andExpect(status().isCreated());

        mockMvc.perform(get("/tickets/tkt_9001/tool-actions").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idempotency_key == '" + keyPrefix + "list')]", hasSize(1)));
        mockMvc.perform(get("/tool-actions/act_missing").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
        execute("act_missing").andExpect(status().isNotFound());
        requestAction("tkt_0000", "escalate_to_human", Map.of("idempotency_key", keyPrefix + "no-ticket"))
                .andExpect(status().isNotFound());
    }

    @Test
    void toolActionsRequireToken() throws Exception {
        mockMvc.perform(post("/tool-actions/act_x/execute")).andExpect(status().isUnauthorized());
    }
}
