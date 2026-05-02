package com.agentdesk.backend.auth;

import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String name,
        String avatarUrl,
        String status
) {
}
