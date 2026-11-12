package com.trustdesk.common;

import com.trustdesk.ai.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Converts exceptions thrown by controllers into {@link ApiError} responses,
 * so every error has the same JSON shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "not_found", e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleUnknownPath(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "not_found", "No endpoint " + e.getResourcePath());
    }

    /** The AI provider failed or returned unusable output. Nothing was stored. */
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiError> handleAiProviderFailure(AiProviderException e) {
        log.warn("AI provider failure: {}", e.getMessage(), e.getCause());
        return error(HttpStatus.BAD_GATEWAY, "ai_provider_error", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException e) {
        return error(HttpStatus.BAD_REQUEST, "validation_error", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException e) {
        return error(HttpStatus.CONFLICT, "conflict", e.getMessage());
    }

    /** Two requests changed the same row at the same time (e.g. two approvals); the second one loses. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, "conflict", "The resource was changed by another request; reload and try again");
    }

    @ExceptionHandler(ActionNotAllowedException.class)
    public ResponseEntity<ApiError> handleActionNotAllowed(ActionNotAllowedException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "action_not_allowed", e.getMessage());
    }

    /** A request body failed its @NotBlank / @Size / ... rules. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "validation_error", message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException e) {
        return error(HttpStatus.BAD_REQUEST, "validation_error",
                "Missing required parameter '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleWrongParameterType(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "validation_error", "Invalid value for '" + e.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "validation_error", "Request body is missing or is not valid JSON");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleWrongMethod(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed",
                e.getMethod() + " is not supported for this endpoint");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        // Log the details for developers; never send internal details to the client.
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Something went wrong");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), error, message));
    }
}