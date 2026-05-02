package com.agentdesk.backend.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!dev")
class InMemorySettingsRepository implements SettingsRepository {

    private final ObjectMapper objectMapper;
    private final Map<UUID, ObjectNode> settingsByProject = new ConcurrentHashMap<>();

    InMemorySettingsRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode settings(UUID projectId) {
        return settingsByProject.computeIfAbsent(projectId, ignored -> defaults()).deepCopy();
    }

    @Override
    public JsonNode patchSettings(UUID projectId, SettingsDtos.PatchSettingsRequest request) {
        ObjectNode current = settingsByProject.computeIfAbsent(projectId, ignored -> defaults());
        merge(current, "context_compression", request.contextCompression());
        merge(current, "backup", request.backup());
        merge(current, "tool_authorization", request.toolAuthorization());
        return current.deepCopy();
    }

    @Override
    public SettingsDtos.TaskQueueSummary taskQueue(UUID projectId) {
        return new SettingsDtos.TaskQueueSummary(0, 0, 0, 0, 0);
    }

    @Override
    public List<SettingsDtos.TaskLogItem> taskLogs(UUID projectId, SettingsDtos.TaskLogQuery query) {
        return List.of();
    }

    private void merge(ObjectNode current, String key, JsonNode value) {
        if (value != null && value.isObject()) {
            current.set(key, value.deepCopy());
        }
    }

    private ObjectNode defaults() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode compression = root.putObject("context_compression");
        compression.put("enabled", true);
        compression.put("strength", "balanced");
        compression.put("trigger_tokens", 12000);
        ObjectNode backup = root.putObject("backup");
        backup.put("enabled", false);
        backup.put("target", "minio");
        backup.put("retention_days", 7);
        ObjectNode tools = root.putObject("tool_authorization");
        tools.put("mode", "approval_required");
        tools.put("secret_policy", "secret_ref_only");
        return root;
    }
}
