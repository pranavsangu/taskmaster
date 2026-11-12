package com.trustdesk.common;

/** Thrown when a requested record does not exist; turned into a 404 response. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}