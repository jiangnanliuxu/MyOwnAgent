package com.agentdesk.backend.auth;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        String avatarUrl
) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.id(), user.email(), user.name(), user.avatarUrl());
    }
}
