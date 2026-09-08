package com.taskmaster.dto.request;

public record TaskQueryParams(
        String status,
        Long assignedTo,
        String search,
        int page,
        int size,
        String sort
) {
    public static TaskQueryParams defaults() {
        return new TaskQueryParams(null, null, null, 0, 20, null);
    }
}
