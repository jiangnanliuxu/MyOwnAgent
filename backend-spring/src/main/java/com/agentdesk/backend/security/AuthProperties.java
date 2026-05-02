package com.agentdesk.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agent-desk.security.jwt")
public record AuthProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
    private static final String LOCAL_DEVELOPMENT_SECRET = "local-development-only-change-this-placeholder-secret-32";

    public String secret() {
        if (secret == null || secret.isBlank()) {
            return LOCAL_DEVELOPMENT_SECRET;
        }
        return secret;
    }

    public Duration accessTokenTtl() {
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            return Duration.ofMinutes(15);
        }
        return accessTokenTtl;
    }

    public Duration refreshTokenTtl() {
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            return Duration.ofDays(7);
        }
        return refreshTokenTtl;
    }
}
