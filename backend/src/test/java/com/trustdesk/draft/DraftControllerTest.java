package com.trustdesk.draft;

import com.trustdesk.triage.TriageResultRepository;
import com.trustdesk.trace.AgentRunRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the real draft workflow with the default MockAiProvider against the seeded database.
 * Drafts and triages created by these tests are deleted afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"trustdesk.demo-token=test-token", "trustdesk.ai.provider=mock"})
class DraftControllerTest {

    private static final String AUTH = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private DraftReplyRepository draftReplyRepository;

    @Autowired
    private TriageResultRepository triageResultRepository;

    private final OffsetDateTime testStart = OffsetDateTime.now();

    @AfterEach
    void deleteAgentRunsCreatedByTest() {
        agentRunRepository.deleteAll(agentRunRepository.findAll().stream()
                .filter(r -> !r.getCreatedAt().isBefore(testStart)).toList());
    }

    @AfterEach
    void deleteRowsCreatedByTest() {
        draftReplyRepository.deleteAll(draftReplyRepository.findAll().stream()
                .filter(d -> !d.getCreatedAt().isBefore(testStart))
                .toList());
        triageResultRepository.deleteAll(triageResultRepository.findAll().stream()
                .filter(t -> !t.getCreatedAt().isBefore(testStart))
                .toList());
    }

    /**
     * Drafts use the ticket's latest stored triage. Triage first, so the test never depends on
     * triages left in the database by earlier manual runs (e.g. with Groq).
     */
    private org.springframework.test.web.servlet.ResultActions draftAfterFreshTriage(String ticketId) throws Exception {
        mockMvc.perform(post("/tickets/" + ticketId + "/triage").header("Authorization", AUTH))
                .andExpect(status().isOk());
        return mockMvc.perform(post("/tickets/" + ticketId + "/draft-reply").header("Authorization", AUTH));
    }

    @Test
    void damagedItemDraftCitesRefundPolicyAndRecommendsApprovalGatedReplacement() throws Exception {
        draftAfterFreshTriage("tkt_9001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft_id", startsWith("draft_")))
                .andExpect(jsonPath("$.ticket_id").value("tkt_9001"))
                .andExpect(jsonPath("$.triage_id", startsWith("tri_")))
                .andExpect(jsonPath("$.status").value("generated"))
                .andExpect(jsonPath("$.citations", contains("KB-REFUND-001")))
                .andExpect(jsonPath("$.recommended_actions", hasSize(1)))
                .andExpect(jsonPath("$.recommended_actions[0].tool_name").value("create_replacement_order"))
                .andExpect(jsonPath("$.recommended_actions[0].requires_human_approval").value(true))
                .andExpect(jsonPath("$.escalation_reason").value(nullValue()))
                .andExpect(jsonPath("$.provider").value("mock"));
    }

    @Test
    void safetyTicketIsEscalatedWithOnlyEscalateToHuman() throws Exception {
        draftAfterFreshTriage("tkt_9004")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("escalated"))
                .andExpect(jsonPath("$.citations", contains("KB-WARRANTY-001")))
                .andExpect(jsonPath("$.recommended_actions", hasSize(1)))
                .andExpect(jsonPath("$.recommended_actions[0].tool_name").value("escalate_to_human"))
                .andExpect(jsonPath("$.escalation_reason", startsWith("Triage flagged")));
    }

    @Test
    void promptInjectionTicketGetsNoCoupon() throws Exception {
        draftAfterFreshTriage("tkt_9006")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("escalated"))
                .andExpect(jsonPath("$.recommended_actions", hasSize(1)))
                .andExpect(jsonPath("$.recommended_actions[0].tool_name").value("escalate_to_human"));
    }

    @Test
    void doubleChargeRetrievesBillingPolicyFirst() throws Exception {
        draftAfterFreshTriage("tkt_9008")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrieved_doc_ids[0]").value("KB-BILLING-001"))
                .andExpect(jsonPath("$.citations", contains("KB-BILLING-001")))
                .andExpect(jsonPath("$.recommended_actions[0].tool_name").value("start_refund_review"));
    }

    @Test
    void softwareLicenseDraftRecommendsNoRefundAndShowsPolicyFacts() throws Exception {
        draftAfterFreshTriage("tkt_9003")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("generated"))
                .andExpect(jsonPath("$.citations", contains("KB-REFUND-001")))
                .andExpect(jsonPath("$.recommended_actions", hasSize(0)))
                .andExpect(jsonPath("$.policy_facts.as_of_date").value("2026-06-01"))
                .andExpect(jsonPath("$.policy_facts.return_window").value("CLOSED"))
                .andExpect(jsonPath("$.policy_facts.items[0].returnable").value(false));
    }

    @Test
    void latestDraftIsStoredAndReturned() throws Exception {
        draftAfterFreshTriage("tkt_9002")
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/tkt_9002/draft-reply").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket_id").value("tkt_9002"))
                .andExpect(jsonPath("$.citations", hasItem("KB-SHIPPING-001")))
                .andExpect(jsonPath("$.recommended_actions[0].tool_name").value("open_carrier_investigation"))
                .andExpect(jsonPath("$.recommended_actions[0].requires_human_approval").value(false));
    }

    @Test
    void unknownTicketReturns404() throws Exception {
        mockMvc.perform(post("/tickets/tkt_0000/draft-reply").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tickets/tkt_0000/draft-reply").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftRequiresToken() throws Exception {
        mockMvc.perform(post("/tickets/tkt_9001/draft-reply"))
                .andExpect(status().isUnauthorized());
    }
}