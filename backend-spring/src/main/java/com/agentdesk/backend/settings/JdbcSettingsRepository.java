package com.agentdesk.backend.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
@Profile("dev")
class JdbcSettingsRepository implements SettingsRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    JdbcSettingsRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode settings(UUID projectId) {
        String value = jdbcClient.sql("SELECT settings::text FROM projects WHERE id = :project_id")
                .param("project_id", projectId)
                .query(String.class)
                .single();
        return withDefaults(readJson(value));
    }

    @Override
    public JsonNode patchSettings(UUID projectId, SettingsDtos.PatchSettingsRequest request) {
        ObjectNode current = (ObjectNode) settings(projectId);
        merge(current, "context_compression", request.contextCompression());
        merge(current, "backup", request.backup());
        merge(current, "tool_authorization", request.toolAuthorization());
        jdbcClient.sql("""
                        UPDATE projects
                        SET settings = CAST(:settings AS jsonb)
                        WHERE id = :project_id
                        """)
                .param("project_id", projectId)
                .param("settings", current.toString())
                .update();
        return current;
    }

    @Override
    public SettingsDtos.TaskQueueSummary taskQueue(UUID projectId) {
        long ragQueued = count("""
                SELECT COUNT(*) FROM rag_index_jobs
                WHERE project_id = :project_id AND status = 'queued'
                """, projectId);
        long running = count("""
                SELECT COUNT(*) FROM rag_index_jobs
                WHERE project_id = :project_id AND status = 'running'
                """, projectId);
        long failed = count("""
                SELECT COUNT(*) FROM rag_index_jobs
                WHERE project_id = :project_id AND status = 'failed'
                """, projectId);
        long completed = count("""
                SELECT COUNT(*) FROM rag_index_jobs
                WHERE project_id = :project_id AND status = 'succeeded'
                """, projectId);
        return new SettingsDtos.TaskQueueSummary(ragQueued, running, failed, completed, ragQueued);
    }

    @Override
    public List<SettingsDtos.TaskLogItem> taskLogs(UUID projectId, SettingsDtos.TaskLogQuery query) {
        String type = query.type();
        String level = query.level();
        boolean hasType = type != null && !type.isBlank();
        boolean hasLevel = level != null && !level.isBlank();
        StringBuilder sql = new StringBuilder("""
                SELECT id, project_id, thread_id, type, level, message, metadata::text AS metadata, created_at
                FROM task_logs
                WHERE project_id = :project_id
                """);
        if (hasType) {
            sql.append(" AND type = :type");
        }
        if (hasLevel) {
            sql.append(" AND level = :level");
        }
        sql.append(" ORDER BY created_at DESC LIMIT :limit");
        var spec = jdbcClient.sql(sql.toString())
                .param("project_id", projectId)
                .param("limit", query.normalizedLimit());
        if (hasType) {
            spec = spec.param("type", type.trim());
        }
        if (hasLevel) {
            spec = spec.param("level", level.trim());
        }
        return spec.query((rs, rowNum) -> new SettingsDtos.TaskLogItem(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("thread_id", UUID.class),
                rs.getString("type"),
                rs.getString("level"),
                rs.getString("message"),
                readJson(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant()
        )).list();
    }

    private long count(String sql, UUID projectId) {
        return jdbcClient.sql(sql)
                .param("project_id", projectId)
                .query(Long.class)
                .single();
    }

    private void merge(ObjectNode current, String key, JsonNode value) {
        if (value != null && value.isObject()) {
            current.set(key, value.deepCopy());
        }
    }

    private JsonNode withDefaults(JsonNode value) {
        ObjectNode root = value != null && value.isObject() ? (ObjectNode) value.deepCopy() : objectMapper.createObjectNode();
        if (!root.has("context_compression")) {
            ObjectNode compression = root.putObject("context_compression");
            compression.put("enabled", true);
            compression.put("strength", "balanced");
            compression.put("trigger_tokens", 12000);
        }
        if (!root.has("backup")) {
            ObjectNode backup = root.putObject("backup");
            backup.put("enabled", false);
            backup.put("target", "minio");
            backup.put("retention_days", 7);
        }
        if (!root.has("tool_authorization")) {
            ObjectNode tools = root.putObject("tool_authorization");
            tools.put("mode", "approval_required");
            tools.put("secret_policy", "secret_ref_only");
        }
        return root;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }
}
