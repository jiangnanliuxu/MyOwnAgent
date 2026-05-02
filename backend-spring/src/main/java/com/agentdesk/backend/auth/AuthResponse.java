package com.agentdesk.backend.auth;

public record AuthResponse(
        UserResponse user,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
