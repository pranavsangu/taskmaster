package com.trustdesk.common;

/**
 * The single JSON error shape returned by every endpoint, e.g.
 * {@code {"status": 404, "error": "not_found", "message": "Ticket tkt_0000 not found"}}.
 */
public record ApiError(int status, String error, String message) {
}