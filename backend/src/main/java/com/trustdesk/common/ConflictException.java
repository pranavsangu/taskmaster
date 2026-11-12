package com.trustdesk.common;

/** Thrown when a request conflicts with the current state (e.g. executing an unapproved action); turned into a 409 response. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
