package com.trustdesk.draft;

import com.trustdesk.ai.AiProvider;
import com.trustdesk.ai.DraftSuggestion;
import com.trustdesk.ai.DraftSuggestion.ActionSuggestion;
import com.trustdesk.ai.TriageSuggestion;
import com.trustdesk.triage.TriageResultRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "AI recommends, code decides": a fake AI returns chosen (sometimes bad) drafts, and these tests
 * check what the draft service keeps, removes or replaces.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "trustdesk.demo-token=test-token")
class DraftValidationTest {

    private static final String AUTH = "Bearer test-token";
    private static final String BODY = "Hi Aisha, sorry about the damage. We can arrange a replacement.";

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
    void fakeProviderTriagesAsRefund() {
        when(aiProvider.name()).thenReturn("fake");
        when(aiProvider.model()).thenReturn("fake-model");
        when(aiProvider.promptVersion()).thenReturn("fake-prompts-v1");
        when(aiProvider.triage(any())).thenReturn(
                new TriageSuggestion("refund", "medium", "frustrated", false, "Damaged item."));
    }

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

    private long draftsSinceTestStart() {
        return draftReplyRepository.findAll().stream().filter(d -> !d.getCreatedAt().isBefore(testStart)).count();
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
    void citationsOfDocumentsNotRetrievedAreRemoved() throws Exception {
        when(aiProvider.draft(any())).thenReturn(new DraftSuggestion(BODY,
                List.of("KB-REFUND-001", "KB-MADE-UP-999", "KB-REFUND-001"), List.of(), false, null));

        draftAfterFreshTriage("tkt_9001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("generated"))
                .andExpect(jsonPath("$.citations", contains("KB-REFUND-001")))
                .andExpect(jsonPath("$.validation_notes", hasItem(
                        "Removed citation 'KB-MADE-UP-999': not one of the retrieved documents.")));
    }

    @Test
    void unknownToolsAndToolsNotAllowedForCategoryAreRemoved() throws Exception {
        when(aiProvider.draft(any())).thenReturn(new DraftSuggestion(BODY, List.of("KB-REFUND-001"),
                List.of(new ActionSuggestion("create_replacement_order", "Damaged on arrival."),
                        new ActionSuggestion("issue_coupon", "Goodwill."),          // shipping/general only
                        new ActionSuggestion("approve_all_refunds", "Policy changed.")),  // not in catalog
                false, null));

        draftAfterFreshTriage("tkt_9001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommended_actions", hasSize(1)))
                .andExpect(jsonPath("$.recommended_actions[0].tool_name").value("create_replacement_order"))
                // From the tool catalog, not from the AI:
                .andExpect(jsonPath("$.recommended_actions[0].requires_human_approval").value(true))
                .andExpect(jsonPath("$.validation_notes", hasSize(2)));
    }

    @Test
    void draftWithoutValidCitationIsEscalatedWithHoldingReply() throws Exception {
        when(aiProvider.draft(any())).thenReturn(new DraftSuggestion(
                "Great news, your refund of 8999 INR has been processed!", List.of("KB-MADE-UP-999"),
                List.of(new ActionSuggestion("start_refund_review", "Customer asked.")), false, null));

        draftAfterFreshTriage("tkt_9001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("escalated"))
                .andExpect(jsonPath("$.body").value(DraftService.HOLDING_REPLY))
                .andExpect(jsonPath("$.citations", hasSize(0)))
                .andExpect(jsonPath("$.recommended_actions", hasSize(1)))
                .andExpect(jsonPath("$.recommended_actions[0].tool_name").value("escalate_to_human"))
                .andExpect(jsonPath("$.escalation_reason").value("No retrieved policy supports this reply."));
    }

    @Test
    void aiEscalationKeepsOnlyEscalateToHuman() throws Exception {
        when(aiProvider.draft(any())).thenReturn(new DraftSuggestion(BODY, List.of("KB-REFUND-001"),
                List.of(new ActionSuggestion("create_replacement_order", "Damaged.")), true, "Unclear damage claim."));

        draftAfterFreshTriage("tkt_9001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("escalated"))
                .andExpect(jsonPath("$.body").value(BODY))
                .andExpect(jsonPath("$.recommended_actions", hasSize(1)))
                .andExpect(jsonPath("$.recommended_actions[0].tool_name").value("escalate_to_human"))
                .andExpect(jsonPath("$.escalation_reason").value("Unclear damage claim."));
    }

    @Test
    void refundReviewForSoftwareLicenseIsRemovedByPolicyRule() throws Exception {
        when(aiProvider.draft(any())).thenReturn(new DraftSuggestion("We can review a refund.", List.of("KB-REFUND-001"),
                List.of(new ActionSuggestion("start_refund_review", "Customer changed their mind.")), false, null));

        draftAfterFreshTriage("tkt_9003")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("generated"))
                .andExpect(jsonPath("$.recommended_actions", hasSize(0)))
                .andExpect(jsonPath("$.validation_notes[0]", startsWith(
                        "Removed action 'start_refund_review': refund review is not supported by the return policy")))
                .andExpect(jsonPath("$.policy_facts.as_of_date").value("2026-06-01"))
                .andExpect(jsonPath("$.policy_facts.items[0].returnable").value(false));
    }

    @Test
    void emptyDraftIsRejectedAndNothingStored() throws Exception {
        when(aiProvider.draft(any())).thenReturn(new DraftSuggestion("   ", List.of("KB-REFUND-001"), List.of(), false, null));

        draftAfterFreshTriage("tkt_9001")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("ai_provider_error"))
                .andExpect(jsonPath("$.message").value("AI returned an empty draft"));

        assertThat(draftsSinceTestStart()).isZero();
    }
}
