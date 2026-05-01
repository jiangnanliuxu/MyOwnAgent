package com.agentdesk.backend.health;

import java.time.Instant;

public record HealthStatus(
        String status,
        String application,
        String version,
        Instant timestamp
) {
}
