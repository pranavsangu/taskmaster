package com.trustdesk.common;

/** Thrown when a request is well-formed but its values are not acceptable; turned into a 400 response. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}