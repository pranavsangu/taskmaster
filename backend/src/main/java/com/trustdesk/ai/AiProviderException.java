package com.trustdesk.ai;

/**
 * The AI provider failed, timed out, or returned output that cannot be used.
 * The message is safe to show to API clients (it never contains keys or raw responses).
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}