package com.agentdesk.backend.auth;

public record UserAccountWithPassword(
        UserAccount user,
        String passwordHash
) {
}
