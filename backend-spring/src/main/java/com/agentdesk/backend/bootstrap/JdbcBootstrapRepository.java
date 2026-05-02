package com.agentdesk.backend.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("dev")
public class JdbcBootstrapRepository implements BootstrapRepository {

    private static final Instant BASE_TIME = Instant.parse("2026-05-02T02:00:00Z");

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcBootstrapRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void ensureSeeded(UUID projectId) {
        upsertBuiltinRoles(projectId);
        upsertSessionRoles(projectId);
        upsertFolders(projectId);
        upsertThreads(projectId);
        upsertThreadRoles(projectId);
        upsertMessages(projectId);
        upsertSkills(projectId);
        upsertMcpEndpoints(projectId);
    }

    @Override
    public Optional<BootstrapResponse.ProjectView> findProject(UUID projectId) {
        return jdbcClient.sql("""
                        SELECT id, name, description
                        FROM projects
                        WHERE id = :project_id AND status = 'active'
                        """)
                .param("project_id", projectId)
                .query((rs, rowNum) -> new BootstrapResponse.ProjectView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("description")
                ))
                .optional();
    }

    @Override
    public BootstrapResponse load(UUID projectId, String activeThreadKey, JsonNode preferences) {
        return new BootstrapResponse(
                findProject(projectId).orElseThrow(),
                activeThreadKey,
                mergeDefaultPreferences(preferences),
                folders(projectId),
                threads(projectId),
                recentMessages(projectId),
                roles(projectId),
                skills(projectId),
                mcpEndpoints(projectId),
                BootstrapSeedData.healthItems()
        );
    }

    @Override
    public boolean threadKeyBelongsToProject(UUID projectId, String threadKey) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM threads
                        WHERE project_id = :project_id
                          AND client_key = :thread_key
                          AND status = 'active'
                        """)
                .param("project_id", projectId)
                .param("thread_key", threadKey)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    private void upsertBuiltinRoles(UUID projectId) {
        for (BootstrapSeedData.SeedRole role : BootstrapSeedData.roles(objectMapper)) {
            upsertRole(projectId, role);
        }
    }

    private void upsertSessionRoles(UUID projectId) {
        for (BootstrapSeedData.SeedThread thread : BootstrapSeedData.threads()) {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("model", "GPT-5.4-mini");
            config.put("provider", "OpenAI");
            config.put("compression", "轻压缩");
            config.put("prompt_prefix", "围绕 " + thread.folder() + " 目录的 " + thread.label() + " 会话处理任务。");
            config.put("source_thread_id", thread.clientKey());
            config.put("source_file", thread.file());
            config.put("source_folder", thread.folder());
            config.put("role_status", thread.roleStatus());
            upsertRole(projectId, new BootstrapSeedData.SeedRole(
                    BootstrapSeedData.sessionRoleKey(thread.clientKey()),
                    thread.label(),
                    thread.label() + "-session",
                    "Session Role",
                    thread.summary(),
                    thread.folder() + " / " + thread.roleStatus(),
                    false,
                    config
            ));
        }
    }

    private void upsertRole(UUID projectId, BootstrapSeedData.SeedRole role) {
        jdbcClient.sql("""
                        INSERT INTO roles (project_id, client_key, name, alias, tag, description, short_description, is_builtin, config)
                        VALUES (:project_id, :client_key, :name, :alias, :tag, :description, :short_description, :is_builtin, CAST(:config AS jsonb))
                        ON CONFLICT (project_id, client_key) DO UPDATE
                        SET name = EXCLUDED.name,
                            alias = EXCLUDED.alias,
                            tag = EXCLUDED.tag,
                            description = EXCLUDED.description,
                            short_description = EXCLUDED.short_description,
                            is_builtin = EXCLUDED.is_builtin,
                            config = EXCLUDED.config
                        """)
                .param("project_id", projectId)
                .param("client_key", role.clientKey())
                .param("name", role.name())
                .param("alias", role.alias())
                .param("tag", role.tag())
                .param("description", role.description())
                .param("short_description", role.shortDescription())
                .param("is_builtin", role.builtin())
                .param("config", role.config().toString())
                .update();
    }

    private void upsertFolders(UUID projectId) {
        int index = 0;
        for (String folder : List.of("src/auth", "src/router", "tests")) {
            jdbcClient.sql("""
                            INSERT INTO folders (project_id, name, path, sort_order)
                            VALUES (:project_id, :name, :path, :sort_order)
                            ON CONFLICT (project_id, name) DO UPDATE
                            SET path = EXCLUDED.path,
                                sort_order = EXCLUDED.sort_order
                            """)
                    .param("project_id", projectId)
                    .param("name", folder)
                    .param("path", folder)
                    .param("sort_order", index++)
                    .update();
        }
    }

    private void upsertThreads(UUID projectId) {
        Map<String, UUID> folderIds = folderIds(projectId);
        Map<String, UUID> roleIds = roleIds(projectId);
        int seedSort = 0;
        for (BootstrapSeedData.SeedThread thread : BootstrapSeedData.threads()) {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("file", thread.file());
            metadata.put("focus_role_key", thread.focusRole());
            metadata.put("session_role_key", BootstrapSeedData.sessionRoleKey(thread.clientKey()));
            metadata.put("seed_sort", seedSort++);
            metadata.set("role_keys", array(thread.roles()));
            jdbcClient.sql("""
                            INSERT INTO threads (project_id, folder_id, client_key, label, summary, focus_role_id, role_status, metadata)
                            VALUES (:project_id, :folder_id, :client_key, :label, :summary, :focus_role_id, :role_status, CAST(:metadata AS jsonb))
                            ON CONFLICT (project_id, client_key) DO UPDATE
                            SET folder_id = EXCLUDED.folder_id,
                                label = EXCLUDED.label,
                                summary = EXCLUDED.summary,
                                focus_role_id = EXCLUDED.focus_role_id,
                                role_status = EXCLUDED.role_status,
                                metadata = EXCLUDED.metadata
                            """)
                    .param("project_id", projectId)
                    .param("folder_id", folderIds.get(thread.folder()))
                    .param("client_key", thread.clientKey())
                    .param("label", thread.label())
                    .param("summary", thread.summary())
                    .param("focus_role_id", roleIds.get(thread.focusRole()))
                    .param("role_status", thread.roleStatus())
                    .param("metadata", metadata.toString())
                    .update();
        }
    }

    private void upsertThreadRoles(UUID projectId) {
        Map<String, UUID> threadIds = threadIds(projectId);
        Map<String, UUID> roleIds = roleIds(projectId);
        for (BootstrapSeedData.SeedThread thread : BootstrapSeedData.threads()) {
            int sortOrder = 0;
            for (String roleKey : thread.roles()) {
                upsertThreadRole(
                        threadIds.get(thread.clientKey()),
                        roleIds.get(roleKey),
                        roleKey.equals(thread.focusRole()),
                        thread.roleStatus(),
                        sortOrder++
                );
            }
            upsertThreadRole(
                    threadIds.get(thread.clientKey()),
                    roleIds.get(BootstrapSeedData.sessionRoleKey(thread.clientKey())),
                    false,
                    thread.roleStatus(),
                    sortOrder
            );
        }
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

    private void upsertMessages(UUID projectId) {
        Map<String, UUID> threadIds = threadIds(projectId);
        for (BootstrapSeedData.SeedThread thread : BootstrapSeedData.threads()) {
            UUID threadId = threadIds.get(thread.clientKey());
            for (BootstrapSeedData.SeedMessage message : BootstrapSeedData.messages(thread)) {
                jdbcClient.sql("""
                                INSERT INTO messages (project_id, thread_id, client_message_id, role, agent_name, content, kind, status, completed_at, created_at)
                                VALUES (:project_id, :thread_id, :client_message_id, :role, :agent_name, :content, 'text', 'completed', :completed_at, :created_at)
                                ON CONFLICT (thread_id, client_message_id) DO NOTHING
                                """)
                        .param("project_id", projectId)
                        .param("thread_id", threadId)
                        .param("client_message_id", "seed-" + message.sortOrder())
                        .param("role", message.role())
                        .param("agent_name", "agent".equals(message.role()) ? message.title() : null)
                        .param("content", message.content())
                        .param("completed_at", Timestamp.from(BASE_TIME.plusSeconds(message.sortOrder())))
                        .param("created_at", Timestamp.from(BASE_TIME.plusSeconds(message.sortOrder())))
                        .update();
            }
        }
    }

    private void upsertSkills(UUID projectId) {
        for (BootstrapSeedData.SeedSkill skill : BootstrapSeedData.skills(objectMapper)) {
            jdbcClient.sql("""
                            INSERT INTO skills (project_id, client_key, name, source, status, scope, mounts, manifest, config)
                            VALUES (:project_id, :client_key, :name, :source, :status, :scope, :mounts, CAST(:manifest AS jsonb), CAST(:config AS jsonb))
                            ON CONFLICT (project_id, client_key) DO UPDATE
                            SET name = EXCLUDED.name,
                                source = EXCLUDED.source,
                                status = EXCLUDED.status,
                                scope = EXCLUDED.scope,
                                mounts = EXCLUDED.mounts,
                                manifest = EXCLUDED.manifest,
                                config = EXCLUDED.config
                            """)
                    .param("project_id", projectId)
                    .param("client_key", skill.clientKey())
                    .param("name", skill.name())
                    .param("source", skill.source())
                    .param("status", skill.status())
                    .param("scope", skill.scope())
                    .param("mounts", skill.mounts().toArray(String[]::new))
                    .param("manifest", skill.manifest().toString())
                    .param("config", withLastRun(skill.config(), skill.lastRun()).toString())
                    .update();
        }
    }

    private void upsertMcpEndpoints(UUID projectId) {
        for (BootstrapSeedData.SeedMcpEndpoint endpoint : BootstrapSeedData.mcpEndpoints()) {
            jdbcClient.sql("""
                            INSERT INTO mcp_endpoints (project_id, client_key, name, transport, status, auth_type, url, tools, latency_ms)
                            VALUES (:project_id, :client_key, :name, :transport, :status, :auth_type, :url, :tools, :latency_ms)
                            ON CONFLICT (project_id, client_key) DO UPDATE
                            SET name = EXCLUDED.name,
                                transport = EXCLUDED.transport,
                                status = EXCLUDED.status,
                                auth_type = EXCLUDED.auth_type,
                                url = EXCLUDED.url,
                                tools = EXCLUDED.tools,
                                latency_ms = EXCLUDED.latency_ms
                            """)
                    .param("project_id", projectId)
                    .param("client_key", endpoint.clientKey())
                    .param("name", endpoint.name())
                    .param("transport", endpoint.transport())
                    .param("status", endpoint.status())
                    .param("auth_type", endpoint.authType())
                    .param("url", endpoint.url())
                    .param("tools", endpoint.tools().toArray(String[]::new))
                    .param("latency_ms", endpoint.latencyMs())
                    .update();
        }
    }

    private List<BootstrapResponse.FolderView> folders(UUID projectId) {
        return jdbcClient.sql("""
                        SELECT f.id, f.name, f.path, f.sort_order,
                               COALESCE(array_agg(t.client_key ORDER BY (t.metadata->>'seed_sort')::int, t.created_at) FILTER (WHERE t.id IS NOT NULL), ARRAY[]::text[]) AS thread_keys
                        FROM folders f
                        LEFT JOIN threads t ON t.folder_id = f.id AND t.status = 'active'
                        WHERE f.project_id = :project_id
                        GROUP BY f.id
                        ORDER BY f.sort_order, f.created_at
                        """)
                .param("project_id", projectId)
                .query((rs, rowNum) -> {
                    List<String> threadKeys = textArray(rs.getArray("thread_keys"));
                    return new BootstrapResponse.FolderView(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("path"),
                            rs.getInt("sort_order"),
                            threadKeys.size(),
                            threadKeys
                    );
                })
                .list();
    }

    private Map<String, BootstrapResponse.ThreadView> threads(UUID projectId) {
        Map<String, List<BootstrapResponse.MessageView>> messages = recentMessages(projectId);
        Map<String, BootstrapResponse.ThreadView> result = new LinkedHashMap<>();
        jdbcClient.sql("""
                        SELECT t.id, t.client_key, t.folder_id, f.name AS folder, t.label, t.summary,
                               t.focus_role_id, fr.client_key AS focus_role_key, t.role_status, t.status, t.metadata::text AS metadata,
                               sr.id AS session_role_id, sr.client_key AS session_role_key
                        FROM threads t
                        JOIN folders f ON f.id = t.folder_id
                        LEFT JOIN roles fr ON fr.id = t.focus_role_id
                        LEFT JOIN roles sr ON sr.client_key = t.metadata->>'session_role_key' AND sr.project_id = t.project_id
                        WHERE t.project_id = :project_id AND t.status = 'active'
                        ORDER BY f.sort_order, (t.metadata->>'seed_sort')::int, t.created_at
                        """)
                .param("project_id", projectId)
                .query((rs, rowNum) -> {
                    JsonNode metadata = readJson(rs.getString("metadata"));
                    String clientKey = rs.getString("client_key");
                    List<String> roleKeys = jsonTextArray(metadata.path("role_keys"));
                    List<BootstrapResponse.MessageView> threadMessages = messages.getOrDefault(clientKey, List.of());
                    BootstrapResponse.MessageView lastMessage = threadMessages.isEmpty() ? null : threadMessages.getLast();
                    BootstrapResponse.ThreadView view = new BootstrapResponse.ThreadView(
                            rs.getObject("id", UUID.class),
                            clientKey,
                            rs.getObject("folder_id", UUID.class),
                            rs.getString("folder"),
                            metadata.path("file").asText(rs.getString("folder")),
                            rs.getString("label"),
                            rs.getString("summary"),
                            rs.getObject("focus_role_id", UUID.class),
                            rs.getString("focus_role_key"),
                            roleKeys,
                            roleKeys,
                            rs.getString("role_status"),
                            rs.getObject("session_role_id", UUID.class),
                            rs.getString("session_role_key"),
                            rs.getString("status"),
                            lastMessage == null ? null : lastMessage.createdAt(),
                            lastMessage == null ? null : lastMessage.content(),
                            new BootstrapResponse.RagSummary(0, 0, null)
                    );
                    result.put(clientKey, view);
                    return view;
                })
                .list();
        return result;
    }

    private Map<String, List<BootstrapResponse.MessageView>> recentMessages(UUID projectId) {
        Map<String, List<BootstrapResponse.MessageView>> result = new LinkedHashMap<>();
        jdbcClient.sql("""
                        SELECT t.client_key, m.id, m.client_message_id, m.role, m.agent_name, m.content, m.kind, m.status, m.created_at
                        FROM messages m
                        JOIN threads t ON t.id = m.thread_id
                        WHERE m.project_id = :project_id
                        ORDER BY t.created_at, m.created_at
                        """)
                .param("project_id", projectId)
                .query((rs, rowNum) -> {
                    String threadKey = rs.getString("client_key");
                    result.computeIfAbsent(threadKey, ignored -> new ArrayList<>()).add(new BootstrapResponse.MessageView(
                            rs.getObject("id", UUID.class),
                            rs.getString("client_message_id"),
                            rs.getString("role"),
                            rs.getString("agent_name"),
                            rs.getString("content"),
                            rs.getString("kind"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant()
                    ));
                    return threadKey;
                })
                .list();
        return result;
    }

    private Map<String, BootstrapResponse.RoleView> roles(UUID projectId) {
        Map<String, BootstrapResponse.RoleView> result = new LinkedHashMap<>();
        jdbcClient.sql("""
                        SELECT id, client_key, name, alias, tag, description, short_description, status, is_builtin, config::text AS config
                        FROM roles
                        WHERE project_id = :project_id
                        ORDER BY is_builtin DESC, created_at
                        """)
                .param("project_id", projectId)
                .query((rs, rowNum) -> {
                    BootstrapResponse.RoleView role = new BootstrapResponse.RoleView(
                            rs.getObject("id", UUID.class),
                            rs.getString("client_key"),
                            rs.getString("name"),
                            rs.getString("alias"),
                            rs.getString("tag"),
                            rs.getString("description"),
                            rs.getString("short_description"),
                            rs.getString("status"),
                            rs.getBoolean("is_builtin"),
                            readJson(rs.getString("config"))
                    );
                    result.put(role.clientKey(), role);
                    return role;
                })
                .list();
        return result;
    }

    private List<BootstrapResponse.SkillView> skills(UUID projectId) {
        return jdbcClient.sql("""
                        SELECT id, client_key, name, source, status, scope, mounts, last_run_at, manifest::text AS manifest, config::text AS config
                        FROM skills
                        WHERE project_id = :project_id
                        ORDER BY created_at
                        """)
                .param("project_id", projectId)
                .query((rs, rowNum) -> {
                    JsonNode config = readJson(rs.getString("config"));
                    Timestamp lastRunAt = rs.getTimestamp("last_run_at");
                    return new BootstrapResponse.SkillView(
                            rs.getObject("id", UUID.class),
                            rs.getString("client_key"),
                            rs.getString("name"),
                            rs.getString("source"),
                            rs.getString("status"),
                            rs.getString("scope"),
                            textArray(rs.getArray("mounts")),
                            lastRunAt == null ? null : lastRunAt.toInstant(),
                            config.path("last_run").asText(null),
                            readJson(rs.getString("manifest")),
                            config
                    );
                })
                .list();
    }

    private List<BootstrapResponse.McpEndpointView> mcpEndpoints(UUID projectId) {
        return jdbcClient.sql("""
                        SELECT id, client_key, name, transport, status, auth_type, url, tools, latency_ms
                        FROM mcp_endpoints
                        WHERE project_id = :project_id
                        ORDER BY created_at
                        """)
                .param("project_id", projectId)
                .query((rs, rowNum) -> {
                    Integer latencyMs = rs.getObject("latency_ms", Integer.class);
                    return new BootstrapResponse.McpEndpointView(
                            rs.getObject("id", UUID.class),
                            rs.getString("client_key"),
                            rs.getString("name"),
                            rs.getString("transport"),
                            rs.getString("status"),
                            rs.getString("auth_type"),
                            rs.getString("auth_type"),
                            rs.getString("url"),
                            textArray(rs.getArray("tools")),
                            latencyMs,
                            latencyMs == null ? null : latencyMs + "ms"
                    );
                })
                .list();
    }

    private Map<String, UUID> folderIds(UUID projectId) {
        Map<String, UUID> result = new LinkedHashMap<>();
        jdbcClient.sql("SELECT id, name FROM folders WHERE project_id = :project_id")
                .param("project_id", projectId)
                .query((rs, rowNum) -> result.put(rs.getString("name"), rs.getObject("id", UUID.class)))
                .list();
        return result;
    }

    private Map<String, UUID> roleIds(UUID projectId) {
        Map<String, UUID> result = new LinkedHashMap<>();
        jdbcClient.sql("SELECT id, client_key FROM roles WHERE project_id = :project_id")
                .param("project_id", projectId)
                .query((rs, rowNum) -> result.put(rs.getString("client_key"), rs.getObject("id", UUID.class)))
                .list();
        return result;
    }

    private Map<String, UUID> threadIds(UUID projectId) {
        Map<String, UUID> result = new LinkedHashMap<>();
        jdbcClient.sql("SELECT id, client_key FROM threads WHERE project_id = :project_id")
                .param("project_id", projectId)
                .query((rs, rowNum) -> result.put(rs.getString("client_key"), rs.getObject("id", UUID.class)))
                .list();
        return result;
    }

    private ObjectNode withLastRun(JsonNode config, String lastRun) {
        ObjectNode objectNode = config != null && config.isObject()
                ? (ObjectNode) config.deepCopy()
                : objectMapper.createObjectNode();
        objectNode.put("last_run", lastRun);
        return objectNode;
    }

    private ObjectNode mergeDefaultPreferences(JsonNode storedPreferences) {
        ObjectNode preferences = objectMapper.createObjectNode();
        preferences.put("layout_density", "紧凑");
        preferences.put("execution_guard", "开启");
        preferences.put("composer_mode", "context_first");
        if (storedPreferences != null && storedPreferences.isObject()) {
            storedPreferences.properties().forEach(entry -> preferences.set(entry.getKey(), entry.getValue()));
        }
        return preferences;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid bootstrap JSON value.", exception);
        }
    }

    private JsonNode array(List<String> values) {
        var arrayNode = objectMapper.createArrayNode();
        values.forEach(arrayNode::add);
        return arrayNode;
    }

    private List<String> jsonTextArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        arrayNode.forEach(value -> values.add(value.asText()));
        return values;
    }

    private List<String> textArray(Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            return Arrays.asList((String[]) array.getArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid text array value.", exception);
        }
    }
}
