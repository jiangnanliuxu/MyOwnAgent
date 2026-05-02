package com.agentdesk.backend.settings;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingsOverviewResponse(
            UUID projectId,
            JsonNode contextCompression,
            JsonNode backup,
            JsonNode toolAuthorization,
            TaskQueueSummary taskQueue,
            List<TaskLogItem> recentLogs
    ) {
    }

    public record PatchSettingsRequest(
            JsonNode contextCompression,
            JsonNode backup,
            JsonNode toolAuthorization
    ) {
    }

    public record TaskLogListResponse(
            List<TaskLogItem> items,
            int limit
    ) {
    }

    public record TaskLogQuery(
            String type,
            String level,
            @Min(1) @Max(100) Integer limit
    ) {
        public int normalizedLimit() {
            return limit == null ? 50 : Math.min(Math.max(limit, 1), 100);
        }
    }

    public record TaskQueueSummary(
            long queued,
            long running,
            long failed,
            long completed,
            long ragIndexQueued
    ) {
    }

    public record TaskLogItem(
            UUID id,
            UUID projectId,
            UUID threadId,
            String type,
            String level,
            String message,
            JsonNode metadata,
            Instant createdAt
    ) {
    }
}
