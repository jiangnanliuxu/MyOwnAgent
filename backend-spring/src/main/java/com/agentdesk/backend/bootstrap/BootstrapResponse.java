package com.agentdesk.backend.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BootstrapResponse(
        ProjectView project,
        String activeThreadKey,
        JsonNode preferences,
        List<FolderView> folders,
        Map<String, ThreadView> threads,
        Map<String, List<MessageView>> recentMessages,
        Map<String, RoleView> roles,
        List<SkillView> skills,
        List<McpEndpointView> mcpEndpoints,
        List<HealthItemView> healthItems
) {

    public record ProjectView(
            UUID id,
            String name,
            String description
    ) {
    }

    public record FolderView(
            UUID id,
            String name,
            String path,
            int sortOrder,
            int threadCount,
            List<String> threadKeys
    ) {
    }

    public record ThreadView(
            UUID id,
            String clientKey,
            UUID folderId,
            String folder,
            String file,
            String label,
            String summary,
            UUID focusRoleId,
            String focusRoleKey,
            List<String> roles,
            List<String> roleKeys,
            String roleStatus,
            UUID sessionRoleId,
            String sessionRoleKey,
            String status,
            Instant lastMessageAt,
            String messagePreview,
            RagSummary rag
    ) {
    }

    public record RagSummary(
            long documentCount,
            long indexedCount,
            String latestStatus
    ) {
    }

    public record MessageView(
            UUID id,
            String clientMessageId,
            String role,
            String agentName,
            String content,
            String kind,
            String status,
            Instant createdAt
    ) {
    }

    public record RoleView(
            UUID id,
            String clientKey,
            String name,
            String alias,
            String tag,
            String description,
            String shortDescription,
            String status,
            boolean isBuiltin,
            JsonNode config
    ) {
    }

    public record SkillView(
            UUID id,
            String clientKey,
            String name,
            String source,
            String status,
            String scope,
            List<String> mounts,
            Instant lastRunAt,
            String lastRun,
            JsonNode manifest,
            JsonNode config
    ) {
    }

    public record McpEndpointView(
            UUID id,
            String clientKey,
            String name,
            String transport,
            String status,
            String authType,
            String auth,
            String url,
            List<String> tools,
            Integer latencyMs,
            String latency
    ) {
    }

    public record HealthItemView(
            String name,
            String status,
            String message
    ) {
    }
}
