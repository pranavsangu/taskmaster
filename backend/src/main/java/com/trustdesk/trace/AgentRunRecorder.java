package com.trustdesk.trace;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves traces in their <b>own</b> transaction (REQUIRES_NEW). The triage and draft workflows run
 * in a transaction that is rolled back when the AI fails; without this, the trace of a failed run
 * would be rolled back too, and failures are exactly what traces are for.
 */
@Component
public class AgentRunRecorder {

    private final AgentRunRepository agentRunRepository;

    public AgentRunRecorder(AgentRunRepository agentRunRepository) {
        this.agentRunRepository = agentRunRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AgentRun run) {
        agentRunRepository.save(run);
    }

    /** Message safe to store and show: our own AI-provider messages as-is, anything else generic. */
    public static String safeError(RuntimeException e) {
        return e instanceof com.trustdesk.ai.AiProviderException ? e.getMessage() : "Internal error during the run";
    }
}
