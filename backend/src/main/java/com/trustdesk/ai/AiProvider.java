package com.trustdesk.ai;

/**
 * The only way the application talks to an AI model. Implementations are swappable
 * (mock for tests, Groq for real runs) and their output is a suggestion only:
 * callers must validate it before storing or acting on it.
 */
public interface AiProvider {

    /** Short name stored with every result, e.g. "mock" or "groq". */
    String name();

    /** The model used, e.g. "keyword-rules-v1" or "openai/gpt-oss-120b". */
    String model();

    /** Version of the prompts (or rules) in use, stored in every trace. Change it whenever prompts change. */
    String promptVersion();

    /**
     * Suggests category, priority, sentiment and escalation for a ticket.
     *
     * @throws AiProviderException if the provider fails, times out, or returns something unreadable
     */
    TriageSuggestion triage(TriageInput input);

    /**
     * Suggests a customer reply with citations and recommended tool actions.
     *
     * @throws AiProviderException if the provider fails, times out, or returns something unreadable
     */
    DraftSuggestion draft(DraftInput input);
}