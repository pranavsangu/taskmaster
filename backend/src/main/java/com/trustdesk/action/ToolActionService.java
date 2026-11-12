package com.trustdesk.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.common.ConflictException;
import com.trustdesk.common.InvalidRequestException;
import com.trustdesk.common.ResourceNotFoundException;
import com.trustdesk.ticket.Ticket;
import com.trustdesk.ticket.TicketRepository;
import com.trustdesk.tool.ToolDefinition;
import com.trustdesk.tool.ToolDefinitionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Request → approve/reject → execute, for tool actions.
 * <p>
 * Idempotency: the idempotency key is unique in the database. Sending the same request again
 * returns the existing action ({@code idempotent_replay: true}); reusing a key for a different
 * request is a 409. If two identical requests arrive at the same moment, the database lets only
 * one insert succeed and the other returns the winner's action.
 */
@Service
public class ToolActionService {

    static final String IDEMPOTENCY_KEY = "idempotency_key";
    static final String IDEMPOTENCY_KEY_PATTERN = "[A-Za-z0-9._:-]{1,200}";
    static final String DEFAULT_REQUESTER = "support_agent";
    /** Actor names that stand for the AI or the system; they may request actions but never approve them. */
    static final Set<String> NON_HUMAN_ACTORS = Set.of("ai", "ai_agent", "assistant", "system", "trustdesk");

    private final ToolActionRequestRepository actionRepository;
    private final ApprovalRepository approvalRepository;
    private final TicketRepository ticketRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final ToolPolicyValidator validator;
    private final ToolExecutor executor;
    private final ObjectMapper objectMapper;
    private final ZoneId businessZone;

    public ToolActionService(ToolActionRequestRepository actionRepository,
                             ApprovalRepository approvalRepository,
                             TicketRepository ticketRepository,
                             ToolDefinitionRepository toolDefinitionRepository,
                             ToolPolicyValidator validator,
                             ToolExecutor executor,
                             ObjectMapper objectMapper,
                             @Value("${trustdesk.business-time-zone}") String businessZone) {
        this.actionRepository = actionRepository;
        this.approvalRepository = approvalRepository;
        this.ticketRepository = ticketRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.validator = validator;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.businessZone = ZoneId.of(businessZone);
    }

    /**
     * Not one transaction on purpose: the insert runs in its own transaction, so that if it loses
     * a race on the idempotency key we can still look up the winning action afterwards.
     */
    public ToolActionResponse request(CreateToolActionRequest body) {
        Map<String, Object> payload = body.payload();
        Object rawKey = payload.get(IDEMPOTENCY_KEY);
        String key = rawKey == null ? "" : rawKey.toString().strip();
        if (!key.matches(IDEMPOTENCY_KEY_PATTERN)) {
            throw new InvalidRequestException("payload.idempotency_key is required: 1-200 letters, digits, '.', '_', ':' or '-'");
        }

        Optional<ToolActionRequest> existing = actionRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return replay(existing.get(), body);
        }

        Ticket ticket = ticketRepository.findWithContextByTicketId(body.ticketId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket " + body.ticketId() + " not found"));
        ToolDefinition tool = toolDefinitionRepository.findById(body.toolName())
                .orElseThrow(() -> new InvalidRequestException("Unknown tool '" + body.toolName() + "'"));
        validator.validate(ticket, tool, payload);

        String requestedBy = body.requestedBy() == null || body.requestedBy().isBlank()
                ? DEFAULT_REQUESTER : body.requestedBy().strip();
        ToolActionRequest action = new ToolActionRequest(
                "act_" + UUID.randomUUID(),
                ticket,
                tool.getToolName(),
                payload,
                tool.getRiskLevel(),
                tool.isRequiresHumanApproval(),
                key,
                requestedBy,
                OffsetDateTime.now(businessZone));
        try {
            actionRepository.saveAndFlush(action);
        } catch (DataIntegrityViolationException e) {
            // Another request with the same key was saved between our lookup and our insert.
            return actionRepository.findByIdempotencyKey(key)
                    .map(winner -> replay(winner, body))
                    .orElseThrow(() -> e);
        }
        return ToolActionResponse.from(action, List.of(), false);
    }

    private ToolActionResponse replay(ToolActionRequest existing, CreateToolActionRequest body) {
        boolean sameRequest = existing.getTicket().getTicketId().equals(body.ticketId())
                && existing.getToolName().equals(body.toolName())
                && objectMapper.valueToTree(existing.getPayload()).equals(objectMapper.valueToTree(body.payload()));
        if (!sameRequest) {
            throw new ConflictException("idempotency_key '" + existing.getIdempotencyKey()
                    + "' was already used for a different request (action " + existing.getActionId() + ")");
        }
        return ToolActionResponse.from(existing, approvals(existing), true);
    }

    @Transactional
    public ToolActionResponse approve(String actionId, ReviewDecisionRequest body) {
        return decide(actionId, body, true);
    }

    @Transactional
    public ToolActionResponse reject(String actionId, ReviewDecisionRequest body) {
        return decide(actionId, body, false);
    }

    private ToolActionResponse decide(String actionId, ReviewDecisionRequest body, boolean approve) {
        ToolActionRequest action = find(actionId);
        String reviewer = body.reviewerId().strip();
        if (NON_HUMAN_ACTORS.contains(reviewer.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("reviewer_id must identify a human reviewer, not '" + reviewer + "'");
        }
        if (reviewer.equalsIgnoreCase(action.getRequestedBy())) {
            throw new InvalidRequestException("reviewer_id must be a different person from the requester ("
                    + action.getRequestedBy() + ")");
        }
        OffsetDateTime now = OffsetDateTime.now(businessZone);
        if (approve) {
            action.approve(now);
        } else {
            action.reject(now);
        }
        approvalRepository.save(new Approval("apr_" + UUID.randomUUID(), action, reviewer,
                approve ? "approved" : "rejected", body.reason().strip(), now));
        return ToolActionResponse.from(action, approvals(action), false);
    }

    /**
     * Runs an approved action once. Executing an already executed action returns the stored
     * result instead of running the tool again. Simultaneous calls: the database lets exactly one
     * claim the action; the others get 409 (or, once it has finished, the stored result).
     */
    @Transactional
    public ToolActionResponse execute(String actionId) {
        ToolActionRequest action = find(actionId);
        if (ToolActionRequest.EXECUTED.equals(action.getStatus())) {
            return ToolActionResponse.from(action, approvals(action), true);
        }
        action.requireExecutable();
        if (actionRepository.claimForExecution(actionId, OffsetDateTime.now(businessZone)) == 0) {
            // Another request claimed it between our read and our update.
            throw new ConflictException("Action " + actionId + " is already being executed by another request");
        }
        action = find(actionId); // reload: now 'executing', with the new version
        OffsetDateTime now = OffsetDateTime.now(businessZone);
        try {
            action.markExecuted(executor.execute(action), now);
        } catch (RuntimeException e) {
            action.markFailed("Tool execution failed: " + e.getMessage(), now);
        }
        return ToolActionResponse.from(action, approvals(action), false);
    }

    @Transactional(readOnly = true)
    public ToolActionResponse get(String actionId) {
        ToolActionRequest action = find(actionId);
        return ToolActionResponse.from(action, approvals(action), false);
    }

    @Transactional(readOnly = true)
    public List<ToolActionResponse> forTicket(String ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new ResourceNotFoundException("Ticket " + ticketId + " not found");
        }
        return actionRepository.findAllByTicketTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(a -> ToolActionResponse.from(a, approvals(a), false))
                .toList();
    }

    private ToolActionRequest find(String actionId) {
        return actionRepository.findById(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Tool action " + actionId + " not found"));
    }

    private List<Approval> approvals(ToolActionRequest action) {
        return approvalRepository.findAllByActionActionIdOrderByCreatedAtAsc(action.getActionId());
    }
}