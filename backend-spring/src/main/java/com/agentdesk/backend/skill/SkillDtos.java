package com.agentdesk.backend.skill;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SkillDtos {

    private SkillDtos() {
    }

    public record SkillListResponse(List<SkillItem> items) {
    }

    public record SkillResponse(SkillItem skill) {
    }

    public record CreateSkillRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 120) String clientKey,
            @NotBlank @Size(max = 160) String source,
            String status,
            @Size(max = 1200) String scope,
            List<String> mounts,
            JsonNode manifest,
            JsonNode config
    ) {
    }

    public record PatchSkillRequest(
            @Size(max = 160) String name,
            @Size(max = 160) String source,
            String status,
            @Size(max = 1200) String scope,
            List<String> mounts,
            JsonNode manifest,
            JsonNode config
    ) {
    }

    public record ToggleSkillRequest(String nextStatus) {
    }

    public record SyncPolicyResponse(
            int enabled,
            int synced,
            Instant syncedAt,
            List<SkillItem> items
    ) {
    }

    public record SkillItem(
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
            JsonNode config,
            Instant createdAt
    ) {
    }
}
