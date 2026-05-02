package com.agentdesk.backend.user;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record UserPreferencesResponse(
        UUID userId,
        String activeThreadKey,
        JsonNode preferences
) {
    public static UserPreferencesResponse from(UserPreferences userPreferences) {
        return new UserPreferencesResponse(
                userPreferences.userId(),
                userPreferences.activeThreadKey(),
                userPreferences.preferences()
        );
    }
}
