package com.agentdesk.backend.auth;

import java.time.Instant;
import java.util.UUID;

public record StoredRefreshToken(
        UUID id,
        UUID userId,
        Instant expiresAt
) {
}
