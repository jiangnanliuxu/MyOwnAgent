package com.agentdesk.backend.role;

import com.agentdesk.backend.bootstrap.BootstrapRepository;
import com.agentdesk.backend.bootstrap.BootstrapSeedData;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("dev")
public class JdbcRoleRepository implements RoleRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final BootstrapRepository bootstrapRepository;

    public JdbcRoleRepository(JdbcClient jdbcClient, ObjectMapper objectMapper, BootstrapRepository bootstrapRepository) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.bootstrapRepository = bootstrapRepository;
    }

    @Override
    public void ensureSeeded(UUID projectId) {
        bootstrapRepository.ensureSeeded(projectId);
    }

    @Override
    public Optional<UUID> findProjectIdByRole(UUID roleId) {
        return jdbcClient.sql("SELECT project_id FROM roles WHERE id = :role_id")
                .param("role_id", roleId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public Optional<UUID> findProjectIdByFolder(UUID folderId) {
        return jdbcClient.sql("SELECT project_id FROM folders WHERE id = :folder_id")
                .param("folder_id", folderId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public List<RoleDtos.RoleItem> listRoles(UUID projectId, boolean includeThreadRoles) {
        return jdbcClient.sql("""
                        SELECT id, client_key, name, alias, tag, description, short_description,
                               status, is_builtin, config::text AS config
                        FROM roles
                        WHERE project_id = :project_id AND status <> 'archived'
                        ORDER BY is_builtin DESC, created_at, client_key
                        """)
                .param("project_id", projectId)
                .query((rs, rowNum) -> roleItem(rs, includeThreadRoles))
                .list();
    }

    @Override
    public Optional<RoleDtos.RoleItem> findRole(UUID roleId, boolean includeThreadRoles) {
        return jdbcClient.sql("""
                        SELECT id, client_key, name, alias, tag, description, short_description,
                               status, is_builtin, config::text AS config
                        FROM roles
                        WHERE id = :role_id
                        """)
                .param("role_id", roleId)
                .query((rs, rowNum) -> roleItem(rs, includeThreadRoles))
                .optional();
    }

    @Override
    @Transactional
    public RoleDtos.RoleUpdateResponse patchRole(UUID roleId, RoleDtos.PatchRoleRequest request) {
        RolePatchTarget current = jdbcClient.sql("""
                        SELECT project_id, name, alias, tag, description, short_description, status, config::text AS config
                        FROM roles
                        WHERE id = :role_id
                        """)
                .param("role_id", roleId)
                .query((rs, rowNum) -> new RolePatchTarget(
                        rs.getObject("project_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("alias"),
                        rs.getString("tag"),
                        rs.getString("description"),
                        rs.getString("short_description"),
                        rs.getString("status"),
                        readJson(rs.getString("config"))
                ))
                .single();

        String status = StringUtils.hasText(request.status()) ? request.status().trim() : current.status();
        if (!List.of("active", "disabled", "archived").contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status must be active, disabled, or archived.");
        }

        JsonNode mergedConfig = mergeConfig(current.config(), request.config(), current.projectId(), roleId);
        jdbcClient.sql("""
                        UPDATE roles
                        SET name = :name,
                            alias = :alias,
                            tag = :tag,
                            description = :description,
                            short_description = :short_description,
                            status = :status,
                            config = CAST(:config AS jsonb)
                        WHERE id = :role_id
                        """)
                .param("role_id", roleId)
                .param("name", valueOrCurrent(request.name(), current.name()))
                .param("alias", valueOrCurrent(request.alias(), current.alias()))
                .param("tag", valueOrCurrent(request.tag(), current.tag()))
                .param("description", request.description() == null ? current.description() : request.description().trim())
                .param("short_description", request.shortDescription() == null ? current.shortDescription() : request.shortDescription().trim())
                .param("status", status)
                .param("config", mergedConfig.toString())
                .update();

        List<RoleDtos.SyncedThread> syncedThreads = syncSourceThread(
                current.projectId(),
                mergedConfig,
                StringUtils.hasText(request.name()),
                request.description() != null,
                valueOrCurrent(request.name(), current.name()),
                request.description() == null ? current.description() : request.description().trim()
        );
        return new RoleDtos.RoleUpdateResponse(findRole(roleId, true).orElseThrow(), syncedThreads);
    }

    @Override
    @Transactional
    public RoleDtos.SyncRolesResponse syncFolderRoles(UUID folderId) {
        FolderRow folder = jdbcClient.sql("""
                        SELECT id, project_id, name
                        FROM folders
                        WHERE id = :folder_id
                        """)
                .param("folder_id", folderId)
                .query((rs, rowNum) -> new FolderRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("name")
                ))
                .single();
        List<ThreadRow> threads = threadRows(folderId);
        int created = 0;
        int synced = 0;

        for (ThreadRow thread : threads) {
            String sessionRoleKey = StringUtils.hasText(thread.sessionRoleKey())
                    ? thread.sessionRoleKey()
                    : BootstrapSeedData.sessionRoleKey(thread.clientKey());
            if (!roleExists(folder.projectId(), sessionRoleKey)) {
                created++;
            }
            upsertSessionRole(folder.projectId(), thread, sessionRoleKey);

            jdbcClient.sql("UPDATE thread_roles SET is_focus = false WHERE thread_id = :thread_id")
                    .param("thread_id", thread.id())
                    .update();
            int sortOrder = 0;
            for (String roleKey : roleKeysWithSession(thread.roleKeys(), sessionRoleKey)) {
                Optional<UUID> roleId = roleId(folder.projectId(), roleKey);
                if (roleId.isPresent()) {
                    boolean focus = roleKey.equals(thread.focusRoleKey());
                    upsertThreadRole(thread.id(), roleId.get(), focus, thread.roleStatus(), sortOrder++);
                    synced++;
                }
            }
        }

        return new RoleDtos.SyncRolesResponse(folder.id(), folder.name(), threadRoles(folderId), created, synced);
    }

    private RoleDtos.RoleItem roleItem(ResultSet rs, boolean includeThreadRoles) throws SQLException {
        UUID roleId = rs.getObject("id", UUID.class);
        return new RoleDtos.RoleItem(
                roleId,
                rs.getString("client_key"),
                rs.getString("name"),
                rs.getString("alias"),
                rs.getString("tag"),
                rs.getString("description"),
                rs.getString("short_description"),
                rs.getString("status"),
                rs.getBoolean("is_builtin"),
                readJson(rs.getString("config")),
                includeThreadRoles ? boundThreads(roleId) : List.of()
        );
    }

    private List<RoleDtos.BoundThread> boundThreads(UUID roleId) {
        return jdbcClient.sql("""
                        SELECT t.id AS thread_id, t.client_key AS thread_client_key, t.label AS thread_label,
                               f.id AS folder_id, f.name AS folder, tr.is_focus, tr.status, tr.sort_order
                        FROM thread_roles tr
                        JOIN threads t ON t.id = tr.thread_id
                        JOIN folders f ON f.id = t.folder_id
                        WHERE tr.role_id = :role_id AND t.status <> 'deleted'
                        ORDER BY f.sort_order, t.created_at, tr.sort_order
                        """)
                .param("role_id", roleId)
                .query((rs, rowNum) -> new RoleDtos.BoundThread(
                        rs.getObject("thread_id", UUID.class),
                        rs.getString("thread_client_key"),
                        rs.getString("thread_label"),
                        rs.getObject("folder_id", UUID.class),
                        rs.getString("folder"),
                        rs.getBoolean("is_focus"),
                        rs.getString("status"),
                        rs.getInt("sort_order")
                ))
                .list();
    }

    private List<RoleDtos.ThreadRoleItem> threadRoles(UUID folderId) {
        return jdbcClient.sql("""
                        SELECT t.id AS thread_id, t.client_key AS thread_client_key, t.label AS thread_label,
                               r.id AS role_id, r.client_key AS role_client_key, r.name AS role_name,
                               tr.is_focus, tr.status, tr.sort_order
                        FROM threads t
                        JOIN thread_roles tr ON tr.thread_id = t.id
                        JOIN roles r ON r.id = tr.role_id
                        WHERE t.folder_id = :folder_id AND t.status <> 'deleted'
                        ORDER BY t.created_at, tr.sort_order
                        """)
                .param("folder_id", folderId)
                .query((rs, rowNum) -> new RoleDtos.ThreadRoleItem(
                        rs.getObject("thread_id", UUID.class),
                        rs.getString("thread_client_key"),
                        rs.getString("thread_label"),
                        rs.getObject("role_id", UUID.class),
                        rs.getString("role_client_key"),
                        rs.getString("role_name"),
                        rs.getBoolean("is_focus"),
                        rs.getString("status"),
                        rs.getInt("sort_order")
                ))
                .list();
    }

    private List<ThreadRow> threadRows(UUID folderId) {
        return jdbcClient.sql("""
                        SELECT t.id, t.project_id, t.client_key, t.label, t.summary, t.role_status,
                               f.name AS folder, t.metadata::text AS metadata, fr.client_key AS focus_role_key
                        FROM threads t
                        JOIN folders f ON f.id = t.folder_id
                        LEFT JOIN roles fr ON fr.id = t.focus_role_id
                        WHERE t.folder_id = :folder_id AND t.status <> 'deleted'
                        ORDER BY t.created_at
                        """)
                .param("folder_id", folderId)
                .query((rs, rowNum) -> {
                    JsonNode metadata = readJson(rs.getString("metadata"));
                    return new ThreadRow(
                            rs.getObject("id", UUID.class),
                            rs.getObject("project_id", UUID.class),
                            rs.getString("client_key"),
                            rs.getString("label"),
                            rs.getString("summary"),
                            metadata.path("file").asText(rs.getString("folder")),
                            rs.getString("folder"),
                            rs.getString("focus_role_key"),
                            jsonTextArray(metadata.path("role_keys")),
                            metadata.path("session_role_key").asText(null),
                            rs.getString("role_status")
                    );
                })
                .list();
    }

    private void upsertSessionRole(UUID projectId, ThreadRow thread, String sessionRoleKey) {
        ObjectNode config = objectMapper.createObjectNode();
        boolean arranged = !"未编排".equals(thread.roleStatus());
        config.put("model", arranged ? "GPT-5.4-mini" : "待编排");
        config.put("provider", arranged ? "OpenAI" : "未选择");
        config.put("compression", arranged ? "轻压缩" : "关闭压缩");
        config.put("prompt_prefix", arranged
                ? "围绕 " + thread.folder() + " 目录的 " + thread.label() + " 会话处理任务。"
                : "这是一个新建但未编排的会话角色，先补齐职责、模型和提示前缀。");
        config.put("source_thread_id", thread.clientKey());
        config.put("source_file", thread.file());
        config.put("source_folder", thread.folder());
        config.put("role_status", thread.roleStatus());
        config.put("matrix_copy", arranged
                ? "绑定 " + thread.folder() + " 目录下的 " + thread.label() + " 会话。"
                : "新增自 " + thread.folder() + " 目录，尚未进入正式角色编排。");
        config.put("handoff", arranged
                ? "将会话上下文整理给主助手，由主助手统一对外输出。"
                : "未编排前只保留为候选角色，不自动接手任务。");
        jdbcClient.sql("""
                        INSERT INTO roles (project_id, client_key, name, alias, tag, description, short_description, is_builtin, config)
                        VALUES (:project_id, :client_key, :name, :alias, :tag, :description, :short_description, false, CAST(:config AS jsonb))
                        ON CONFLICT (project_id, client_key) DO UPDATE
                        SET description = EXCLUDED.description,
                            short_description = EXCLUDED.short_description,
                            config = roles.config || EXCLUDED.config
                        """)
                .param("project_id", projectId)
                .param("client_key", sessionRoleKey)
                .param("name", thread.label())
                .param("alias", thread.label() + "-session")
                .param("tag", arranged ? "Session Role" : "未编排角色")
                .param("description", thread.summary())
                .param("short_description", thread.folder() + " / " + thread.roleStatus())
                .param("config", config.toString())
                .update();
    }

    private void upsertThreadRole(UUID threadId, UUID roleId, boolean focus, String status, int sortOrder) {
        jdbcClient.sql("""
                        INSERT INTO thread_roles (thread_id, role_id, is_focus, status, sort_order)
                        VALUES (:thread_id, :role_id, :is_focus, :status, :sort_order)
                        ON CONFLICT (thread_id, role_id) DO UPDATE
                        SET is_focus = EXCLUDED.is_focus,
                            status = EXCLUDED.status,
                            sort_order = EXCLUDED.sort_order
                        """)
                .param("thread_id", threadId)
                .param("role_id", roleId)
                .param("is_focus", focus)
                .param("status", status)
                .param("sort_order", sortOrder)
                .update();
    }

    private List<RoleDtos.SyncedThread> syncSourceThread(
            UUID projectId,
            JsonNode config,
            boolean updateLabel,
            boolean updateSummary,
            String nextLabel,
            String nextSummary
    ) {
        String sourceThreadId = sourceThreadId(config);
        if (!StringUtils.hasText(sourceThreadId) || (!updateLabel && !updateSummary)) {
            return List.of();
        }
        List<RoleDtos.SyncedThread> targets = jdbcClient.sql("""
                        SELECT id, client_key
                        FROM threads
                        WHERE project_id = :project_id
                          AND status <> 'deleted'
                          AND (client_key = :source_thread_id OR id::text = :source_thread_id)
                        """)
                .param("project_id", projectId)
                .param("source_thread_id", sourceThreadId)
                .query((rs, rowNum) -> new RoleDtos.SyncedThread(
                        rs.getObject("id", UUID.class),
                        rs.getString("client_key"),
                        updateLabel,
                        updateSummary
                ))
                .list();
        for (RoleDtos.SyncedThread target : targets) {
            jdbcClient.sql("""
                            UPDATE threads
                            SET label = CASE WHEN :update_label THEN :label ELSE label END,
                                summary = CASE WHEN :update_summary THEN :summary ELSE summary END
                            WHERE id = :thread_id
                            """)
                    .param("thread_id", target.threadId())
                    .param("update_label", updateLabel)
                    .param("label", nextLabel)
                    .param("update_summary", updateSummary)
                    .param("summary", nextSummary)
                    .update();
        }
        return targets;
    }

    private JsonNode mergeConfig(JsonNode current, JsonNode patch, UUID projectId, UUID roleId) {
        ObjectNode merged = current != null && current.isObject()
                ? (ObjectNode) current.deepCopy()
                : objectMapper.createObjectNode();
        if (patch == null || patch.isNull()) {
            return merged;
        }
        if (!patch.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "config must be a JSON object.");
        }
        patch.fields().forEachRemaining(entry -> {
            if ("api_key".equals(entry.getKey()) || "apiKey".equals(entry.getKey())) {
                if (entry.getValue().isTextual() && StringUtils.hasText(entry.getValue().asText())) {
                    merged.put("secret_ref", "secret://project/" + projectId + "/roles/" + roleId + "/api-key");
                }
            } else {
                merged.set(entry.getKey(), entry.getValue());
            }
        });
        merged.remove("api_key");
        merged.remove("apiKey");
        return merged;
    }

    private Optional<UUID> roleId(UUID projectId, String roleKey) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM roles
                        WHERE project_id = :project_id AND client_key = :role_key AND status <> 'archived'
                        """)
                .param("project_id", projectId)
                .param("role_key", roleKey)
                .query(UUID.class)
                .optional();
    }

    private boolean roleExists(UUID projectId, String roleKey) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM roles
                        WHERE project_id = :project_id AND client_key = :role_key
                        """)
                .param("project_id", projectId)
                .param("role_key", roleKey)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid role JSON value.", exception);
        }
    }

    private List<String> jsonTextArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        arrayNode.forEach(value -> values.add(value.asText()));
        return values;
    }

    private List<String> roleKeysWithSession(List<String> roleKeys, String sessionRoleKey) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> effectiveRoleKeys = roleKeys == null || roleKeys.isEmpty() ? List.of("primary", "review") : roleKeys;
        effectiveRoleKeys.forEach(roleKey -> result.put(roleKey, roleKey));
        result.put(sessionRoleKey, sessionRoleKey);
        return new ArrayList<>(result.keySet());
    }

    private String sourceThreadId(JsonNode config) {
        if (config == null) {
            return null;
        }
        String sourceThreadId = config.path("source_thread_id").asText(null);
        return StringUtils.hasText(sourceThreadId) ? sourceThreadId : config.path("sourceThreadId").asText(null);
    }

    private String valueOrCurrent(String value, String current) {
        return StringUtils.hasText(value) ? value.trim() : current;
    }

    private record RolePatchTarget(
            UUID projectId,
            String name,
            String alias,
            String tag,
            String description,
            String shortDescription,
            String status,
            JsonNode config
    ) {
    }

    private record FolderRow(UUID id, UUID projectId, String name) {
    }

    private record ThreadRow(
            UUID id,
            UUID projectId,
            String clientKey,
            String label,
            String summary,
            String file,
            String folder,
            String focusRoleKey,
            List<String> roleKeys,
            String sessionRoleKey,
            String roleStatus
    ) {
    }
}
