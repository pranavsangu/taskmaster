package com.trustdesk.triage;

import com.trustdesk.ai.AiProvider;
import com.trustdesk.ai.AiProviderException;
import com.trustdesk.ai.TriageInput;
import com.trustdesk.ai.TriageInput.PolicyExcerpt;
import com.trustdesk.ai.TriageSuggestion;
import com.trustdesk.common.ResourceNotFoundException;
import com.trustdesk.guardrail.GuardrailFinding;
import com.trustdesk.guardrail.GuardrailResult;
import com.trustdesk.guardrail.GuardrailService;
import com.trustdesk.kb.KbDocument;
import com.trustdesk.kb.KbDocumentRepository;
import com.trustdesk.kb.KbSearchResponse;
import com.trustdesk.kb.KbSearchService;
import com.trustdesk.order.Order;
import com.trustdesk.order.OrderItem;
import com.trustdesk.ticket.Ticket;
import com.trustdesk.ticket.TicketRepository;
import com.trustdesk.trace.AgentRun;
import com.trustdesk.trace.AgentRunRecorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triage workflow: load ticket → retrieve policies → ask the AI → validate in Java → store.
 * <p>
 * AI recommends, code decides: the AI's answer is only stored if every value is on an allowed
 * list, and code-level rules can tighten (never loosen) the escalation decision: urgent priority
 * or a guardrail finding in the ticket always escalates. Documents the guardrails flag as
 * untrusted are never shown to the AI.
 * Evaluation labels are never loaded here.
 */
@Service
public class TriageService {

    public static final Set<String> CATEGORIES =
            Set.of("shipping", "refund", "warranty", "billing", "account_security", "general");
    public static final Set<String> PRIORITIES = Set.of("low", "medium", "high", "urgent");
    public static final Set<String> SENTIMENTS = Set.of("positive", "neutral", "frustrated", "worried", "angry");

    static final int POLICIES_FOR_TRIAGE = 3;
    static final int MAX_REASON_LENGTH = 500;

    private final TicketRepository ticketRepository;
    private final TriageResultRepository triageResultRepository;
    private final KbSearchService kbSearchService;
    private final KbDocumentRepository kbDocumentRepository;
    private final GuardrailService guardrailService;
    private final AiProvider aiProvider;
    private final AgentRunRecorder agentRunRecorder;
    private final ZoneId businessZone;

    public TriageService(TicketRepository ticketRepository,
                         TriageResultRepository triageResultRepository,
                         KbSearchService kbSearchService,
                         KbDocumentRepository kbDocumentRepository,
                         GuardrailService guardrailService,
                         AiProvider aiProvider,
                         AgentRunRecorder agentRunRecorder,
                         @Value("${trustdesk.business-time-zone}") String businessZone) {
        this.ticketRepository = ticketRepository;
        this.triageResultRepository = triageResultRepository;
        this.kbSearchService = kbSearchService;
        this.kbDocumentRepository = kbDocumentRepository;
        this.guardrailService = guardrailService;
        this.aiProvider = aiProvider;
        this.agentRunRecorder = agentRunRecorder;
        this.businessZone = ZoneId.of(businessZone);
    }

    @Transactional
    public TriageResponse triage(String ticketId) {
        Ticket ticket = ticketRepository.findWithContextByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " not found"));
        String runId = "run_" + UUID.randomUUID();
        long start = System.nanoTime();

        List<GuardrailFinding> findings = new ArrayList<>(guardrailService.checkTicket(ticket));
        // Search a little deeper, so removing an untrusted document still leaves enough policies.
        List<KbSearchResponse.Result> results = kbSearchService
                .search(ticket.getSubject() + " " + ticket.getBody(), POLICIES_FOR_TRIAGE + 2)
                .results();
        Map<String, KbDocument> docs = kbDocumentRepository
                .findAllById(results.stream().map(KbSearchResponse.Result::docId).toList()).stream()
                .collect(Collectors.toMap(KbDocument::getDocId, Function.identity()));
        GuardrailService.Screened screened = guardrailService.screenDocuments(
                results.stream().map(r -> docs.get(r.docId())).filter(d -> d != null).toList());
        findings.addAll(screened.findings());
        List<String> safeIds = screened.kept().stream().map(KbDocument::getDocId).toList();
        List<KbSearchResponse.Result> policies = results.stream()
                .filter(r -> safeIds.contains(r.docId()))
                .limit(POLICIES_FOR_TRIAGE)
                .toList();
        GuardrailResult guardrail = GuardrailResult.of(findings);
        List<String> policyIds = policies.stream().map(KbSearchResponse.Result::docId).toList();

        TriageSuggestion suggestion;
        String category;
        String priority;
        try {
            suggestion = aiProvider.triage(toInput(ticket, policies));
            category = requireAllowed("category", suggestion.category(), CATEGORIES);
            priority = requireAllowed("priority", suggestion.priority(), PRIORITIES);
        } catch (RuntimeException e) {
            agentRunRecorder.record(trace(runId, ticket, AgentRun.FAILED, null, policyIds, guardrail, start,
                    AgentRunRecorder.safeError(e)));
            throw e;
        }
        String sentiment = normalize(suggestion.sentiment());
        if (!SENTIMENTS.contains(sentiment)) {
            sentiment = "unknown"; // informational only; not worth failing the whole triage
        }
        // Code-level rules: urgent tickets (e.g. safety issues) and tickets with guardrail findings
        // (injection, secret requests, skipping verification) always go to a human.
        boolean ticketFlagged = findings.stream().anyMatch(f -> "ticket".equals(f.source()));
        boolean shouldEscalate = suggestion.shouldEscalate() || "urgent".equals(priority) || ticketFlagged;
        String reason = cleanReason(suggestion.reasonSummary());

        TriageResult result = triageResultRepository.save(new TriageResult(
                "tri_" + UUID.randomUUID(),
                runId,
                ticket,
                category,
                priority,
                sentiment,
                shouldEscalate,
                reason,
                policyIds,
                guardrail,
                aiProvider.name(),
                aiProvider.model(),
                OffsetDateTime.now(businessZone)));
        agentRunRecorder.record(trace(runId, ticket, shouldEscalate ? "escalated" : "completed", result.getTriageId(),
                policyIds, guardrail, start, null));
        return TriageResponse.from(result);
    }

    private AgentRun trace(String runId, Ticket ticket, String status, String resultId, List<String> docIds,
                           GuardrailResult guardrail, long startNanos, String error) {
        return new AgentRun(runId, ticket, AgentRun.TRIAGE, status, resultId, docIds, List.of(), guardrail,
                aiProvider.name(), aiProvider.model(), aiProvider.promptVersion(),
                (System.nanoTime() - startNanos) / 1_000_000, error, OffsetDateTime.now(businessZone));
    }

    @Transactional(readOnly = true)
    public TriageResponse latestTriage(String ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket " + ticketId + " not found");
        }
        return triageResultRepository.findFirstByTicketTicketIdOrderByCreatedAtDesc(ticketId)
                .map(TriageResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + ticketId + " has not been triaged yet"));
    }

    /** The latest stored triage, or a new one if the ticket has never been triaged. */
    @Transactional
    public TriageResponse currentTriage(String ticketId) {
        return triageResultRepository.findFirstByTicketTicketIdOrderByCreatedAtDesc(ticketId)
                .map(TriageResponse::from)
                .orElseGet(() -> triage(ticketId));
    }

    private static TriageInput toInput(Ticket ticket, List<KbSearchResponse.Result> policies) {
        Order order = ticket.getOrder();
        return new TriageInput(
                ticket.getTicketId(),
                ticket.getChannel(),
                ticket.getSubject(),
                ticket.getBody(),
                ticket.getCustomer().getTier(),
                ticket.getCustomer().isVerified(),
                order == null ? null : order.getStatus(),
                order == null ? List.of() : order.getItems().stream().map(OrderItem::name).toList(),
                policies.stream().map(p -> new PolicyExcerpt(p.docId(), p.title(), p.snippet())).toList());
    }

    private static String requireAllowed(String field, String value, Set<String> allowed) {
        String normalized = normalize(value);
        if (!allowed.contains(normalized)) {
            throw new AiProviderException("AI returned an invalid " + field + ": '" + value + "'");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "No reason given.";
        }
        String oneLine = reason.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= MAX_REASON_LENGTH ? oneLine : oneLine.substring(0, MAX_REASON_LENGTH) + "…";
    }
}
