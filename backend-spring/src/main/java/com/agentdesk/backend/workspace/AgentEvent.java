package com.agentdesk.backend.workspace;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AgentEvent(
        long id,
        UUID threadId,
        String type,
        JsonNode data,
        Instant createdAt
) {
}
