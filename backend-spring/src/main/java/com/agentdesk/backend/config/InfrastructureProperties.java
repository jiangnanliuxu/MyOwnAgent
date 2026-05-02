package com.agentdesk.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent-desk.infrastructure")
public record InfrastructureProperties(
        boolean enabled,
        Postgres postgres,
        Redis redis,
        Minio minio,
        Milvus milvus
) {
    public record Postgres(
            String host,
            int port,
            String database,
            String username
    ) {
    }

    public record Redis(
            String host,
            int port
    ) {
    }

    public record Minio(
            String endpoint,
            String bucket,
            String accessKey,
            String secretKey
    ) {
    }

    public record Milvus(
            boolean enabled,
            String endpoint
    ) {
    }
}
