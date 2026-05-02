package com.agentdesk.backend.auth;

import java.util.UUID;

public record RegisteredUser(
        UserAccount user,
        UUID projectId
) {
}
