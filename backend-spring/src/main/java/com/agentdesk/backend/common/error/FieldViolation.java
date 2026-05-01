package com.agentdesk.backend.common.error;

public record FieldViolation(
        String field,
        String message
) {
}
