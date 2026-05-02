package com.agentdesk.backend.auth;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        String avatarUrl,
        UUID defaultProjectId
) {
    public static UserResponse from(UserAccount user) {
        return from(user, null);
    }

    public static UserResponse from(UserAccount user, UUID defaultProjectId) {
        return new UserResponse(user.id(), user.email(), user.name(), user.avatarUrl(), defaultProjectId);
    }
}
