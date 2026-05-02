package com.agentdesk.backend.auth;

record TokenBundle(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
