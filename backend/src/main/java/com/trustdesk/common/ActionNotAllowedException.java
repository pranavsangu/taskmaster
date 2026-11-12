package com.trustdesk.common;

/**
 * Thrown when a tool action is well-formed but policy forbids it (wrong category, amount over
 * the limit, outside the return window, ...); turned into a 422 response.
 */
public class ActionNotAllowedException extends RuntimeException {

    public ActionNotAllowedException(String message) {
        super(message);
    }
}
