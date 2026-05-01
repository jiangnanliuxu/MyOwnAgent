package com.agentdesk.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "agent-desk.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {
    public List<String> allowedOrigins() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return List.of("http://localhost:4173", "http://127.0.0.1:4173");
        }
        return allowedOrigins;
    }
}
