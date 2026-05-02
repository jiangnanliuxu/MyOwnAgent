package com.agentdesk.backend.auth;

import java.util.UUID;

public record AuthResponse(
        UserResponse user,
        UUID defaultProjectId,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
