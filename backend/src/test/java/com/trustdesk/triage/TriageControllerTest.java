package com.trustdesk.triage;

import com.trustdesk.trace.AgentRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real triage workflow with the default MockAiProvider against the seeded database.
 * Triage rows created by these tests are deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"trustdesk.demo-token=test-token", "trustdesk.ai.provider=mock"})
class TriageControllerTest {

    private static final String AUTH = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private TriageResultRepository triageResultRepository;

    private final OffsetDateTime testStart = OffsetDateTime.now();

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

    @Test
    void triagesDamagedEarbudsTicket() throws Exception {
        mockMvc.perform(post("/tickets/tkt_9001/triage").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triage_id", startsWith("tri_")))
                .andExpect(jsonPath("$.ticket_id").value("tkt_9001"))
                .andExpect(jsonPath("$.category").value("refund"))
                .andExpect(jsonPath("$.priority").value("medium"))
                .andExpect(jsonPath("$.should_escalate").value(false))
                .andExpect(jsonPath("$.retrieved_doc_ids", hasItem("KB-REFUND-001")))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.reason_summary", startsWith("Mock triage")));
    }

    @Test
    void urgentSafetyTicketIsEscalated() throws Exception {
        mockMvc.perform(post("/tickets/tkt_9004/triage").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("warranty"))
                .andExpect(jsonPath("$.priority").value("urgent"))
                .andExpect(jsonPath("$.should_escalate").value(true));
    }

    @Test
    void latestTriageIsStoredAndReturned() throws Exception {
        mockMvc.perform(post("/tickets/tkt_9008/triage").header("Authorization", AUTH))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/tkt_9008/triage").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket_id").value("tkt_9008"))
                .andExpect(jsonPath("$.category").value("billing"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.created_at", org.hamcrest.Matchers.endsWith("+05:30")));
    }

    @Test
    void unknownTicketReturns404() throws Exception {
        mockMvc.perform(post("/tickets/tkt_0000/triage").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tickets/tkt_0000/triage").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void triageRequiresToken() throws Exception {
        mockMvc.perform(post("/tickets/tkt_9001/triage"))
                .andExpect(status().isUnauthorized());
    }
}