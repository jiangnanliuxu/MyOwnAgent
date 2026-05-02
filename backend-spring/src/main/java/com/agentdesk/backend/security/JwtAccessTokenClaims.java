package com.agentdesk.backend.security;

import java.time.Instant;
import java.util.UUID;

public record JwtAccessTokenClaims(
        UUID userId,
        String email,
        String name,
        Instant expiresAt
) {
}
