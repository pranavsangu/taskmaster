package com.trustdesk.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.trace.AgentRunRepository;
import com.trustdesk.triage.TriageResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simultaneous requests on one action: a sensitive tool must run at most once, and an action can
 * be decided only once. The tool is made slow so the requests really overlap.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"trustdesk.demo-token=test-token", "trustdesk.ai.provider=mock"})
class ToolActionConcurrencyTest {

    private static final String AUTH = "Bearer test-token";
    private static final int THREADS = 6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ToolActionRequestRepository actionRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private TriageResultRepository triageResultRepository;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @MockitoSpyBean
    private ToolExecutor toolExecutor;

    private final OffsetDateTime testStart = OffsetDateTime.now();
    private final String key = "test-" + UUID.randomUUID();

    @BeforeEach
    void slowTool() {
        doAnswer(invocation -> {
            Thread.sleep(300);
            return invocation.callRealMethod();
        }).when(toolExecutor).execute(any());
    }

    @AfterEach
    void deleteRowsCreatedByTest() {
        approvalRepository.deleteAll(approvalRepository.findAll().stream()
                .filter(a -> !a.getCreatedAt().isBefore(testStart)).toList());
        actionRepository.deleteAll(actionRepository.findAll().stream()
                .filter(a -> a.getIdempotencyKey().equals(key)).toList());
        agentRunRepository.deleteAll(agentRunRepository.findAll().stream()
                .filter(r -> !r.getCreatedAt().isBefore(testStart)).toList());
        triageResultRepository.deleteAll(triageResultRepository.findAll().stream()
                .filter(t -> !t.getCreatedAt().isBefore(testStart)).toList());
    }

    private String requestReplacement() throws Exception {
        mockMvc.perform(post("/tickets/tkt_9001/triage").header("Authorization", AUTH)).andExpect(status().isOk());
        String body = objectMapper.writeValueAsString(Map.of("ticket_id", "tkt_9001", "tool_name", "create_replacement_order",
                "payload", Map.of("order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "Damaged", "idempotency_key", key)));
        String response = mockMvc.perform(post("/tool-actions").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("action_id").asText();
    }

    private void approve(String actionId) throws Exception {
        mockMvc.perform(post("/tool-actions/" + actionId + "/approve").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer_id\": \"manager_priya\", \"reason\": \"Checked\"}"))
                .andExpect(status().isOk());
    }

    /** Fires the same call from several threads at the same moment and returns the HTTP status codes. */
    private List<Integer> simultaneously(Callable<Integer> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : futures) {
                codes.add(f.get());
            }
            return codes;
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void simultaneousExecutesRunTheToolExactlyOnce() throws Exception {
        String id = requestReplacement();
        approve(id);

        List<Integer> codes = simultaneously(() -> mockMvc.perform(
                post("/tool-actions/" + id + "/execute").header("Authorization", AUTH)).andReturn().getResponse().getStatus());

        verify(toolExecutor, times(1)).execute(any());
        assertThat(codes).contains(200).allMatch(c -> c == 200 || c == 409);
        mockMvc.perform(get("/tool-actions/" + id).header("Authorization", AUTH))
                .andExpect(jsonPath("$.status").value("executed"));
    }

    @Test
    void simultaneousApprovalsRecordOneDecision() throws Exception {
        String id = requestReplacement();

        List<Integer> codes = simultaneously(() -> mockMvc.perform(post("/tool-actions/" + id + "/approve")
                        .header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer_id\": \"manager_priya\", \"reason\": \"Checked\"}"))
                .andReturn().getResponse().getStatus());

        assertThat(codes.stream().filter(c -> c == 200).count()).isEqualTo(1);
        assertThat(codes).allMatch(c -> c == 200 || c == 409);
        assertThat(approvalRepository.findAllByActionActionIdOrderByCreatedAtAsc(id)).hasSize(1);
    }

    @Test
    void failingToolIsRecordedAndCannotRunAgain() throws Exception {
        doThrow(new IllegalStateException("carrier system unavailable")).when(toolExecutor).execute(any());
        String id = requestReplacement();
        approve(id);

        mockMvc.perform(post("/tool-actions/" + id + "/execute").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.failure_reason").value("Tool execution failed: carrier system unavailable"))
                .andExpect(jsonPath("$.result").doesNotExist());

        // A failed action is not retried automatically; a human must request a new one.
        mockMvc.perform(post("/tool-actions/" + id + "/execute").header("Authorization", AUTH))
                .andExpect(status().isConflict());
        verify(toolExecutor, times(1)).execute(any());
    }
}
