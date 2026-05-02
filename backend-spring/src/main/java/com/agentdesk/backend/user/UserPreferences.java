package com.agentdesk.backend.user;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record UserPreferences(
        UUID userId,
        String activeThreadKey,
        JsonNode preferences
) {
}
