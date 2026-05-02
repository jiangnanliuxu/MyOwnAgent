package com.agentdesk.backend.workspace;

import com.agentdesk.backend.bootstrap.BootstrapRepository;
import com.agentdesk.backend.bootstrap.BootstrapResponse;
import com.agentdesk.backend.bootstrap.BootstrapSeedData;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
public class JdbcWorkspaceRepository implements WorkspaceRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final BootstrapRepository bootstrapRepository;

    public JdbcWorkspaceRepository(JdbcClient jdbcClient, ObjectMapper objectMapper, BootstrapRepository bootstrapRepository) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.bootstrapRepository = bootstrapRepository;
    }

    @Override
    public void ensureSeeded(UUID projectId) {
        bootstrapRepository.ensureSeeded(projectId);
    }

    @Override
    public List<WorkspaceDtos.FolderItem> listFolders(UUID projectId, boolean includeThreads) {
        return folderItems(projectId, includeThreads);
    }

    @Override
    @Transactional
    public WorkspaceDtos.CreateFolderResponse createFolder(UUID projectId, WorkspaceDtos.CreateFolderRequest request) {
        String name = normalizeName(request.name());
        try {
            FolderRow folder = jdbcClient.sql("""
                            INSERT INTO folders (project_id, name, path, sort_order)
                            VALUES (:project_id, :name, :path, :sort_order)
                            RETURNING id, name, path, sort_order, created_at
                            """)
                    .param("project_id", projectId)
                    .param("name", name)
                    .param("path", StringUtils.hasText(request.path()) ? request.path().trim() : name)
                    .param("sort_order", request.sortOrder() == null ? 0 : request.sortOrder())
                    .query(this::folderRow)
                    .single();

            BootstrapResponse.ThreadView defaultThread = createThreadInternal(
                    projectId,
                    folder.id(),
                    "primary-agent",
                    "新关联目录已加入，等待补充任务目标",
                    List.of("primary", "review"),
                    "primary",
                    "未编排",
                    folder.path()
            );
            List<BootstrapResponse.MessageView> messages = List.of(
                    insertMessage(projectId, defaultThread.id(), "seed-folder-welcome", "agent", "主助手",
                            "已关联 " + name + " 目录。你可以直接在输入框里描述要分析、修改或验证的目标。", "completed"),
                    insertMessage(projectId, defaultThread.id(), "seed-folder-review", "agent", "review-agent",
                            "我会先把这个目录作为当前上下文，不会影响其他目录下已有会话的消息记录。", "completed")
            );

            return new WorkspaceDtos.CreateFolderResponse(folderItem(folder, List.of(defaultThread), true), defaultThread, messages);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Folder already exists.");
        }
    }

    @Override
    public Optional<UUID> findProjectIdByFolder(UUID folderId) {
        return jdbcClient.sql("SELECT project_id FROM folders WHERE id = :id")
                .param("id", folderId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public Optional<UUID> findProjectIdByThread(UUID threadId) {
        return jdbcClient.sql("SELECT project_id FROM threads WHERE id = :id AND status <> 'deleted'")
                .param("id", threadId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public List<BootstrapResponse.ThreadView> listThreads(UUID folderId) {
        return threadViews("""
                        SELECT t.id, t.client_key, t.folder_id, f.name AS folder, t.label, t.summary,
                               t.focus_role_id, fr.client_key AS focus_role_key, t.role_status, t.status, t.metadata::text AS metadata,
                               sr.id AS session_role_id, sr.client_key AS session_role_key
                        FROM threads t
                        JOIN folders f ON f.id = t.folder_id
                        LEFT JOIN roles fr ON fr.id = t.focus_role_id
                        LEFT JOIN roles sr ON sr.client_key = t.metadata->>'session_role_key' AND sr.project_id = t.project_id
                        WHERE t.folder_id = :folder_id AND t.status <> 'deleted'
                        ORDER BY (t.metadata->>'seed_sort')::int NULLS LAST, t.created_at
                        """, Map.of("folder_id", folderId));
    }

    @Override
    @Transactional
    public WorkspaceDtos.CreateThreadResponse createThread(UUID folderId, WorkspaceDtos.CreateThreadRequest request) {
        FolderProject folder = folderProject(folderId);
        int next = jdbcClient.sql("SELECT COUNT(*) + 1 FROM threads WHERE folder_id = :folder_id AND status <> 'deleted'")
                .param("folder_id", folderId)
                .query(Integer.class)
                .single();
        String label = StringUtils.hasText(request.label()) ? request.label().trim() : "会话 " + next;
        String summary = StringUtils.hasText(request.summary()) ? request.summary().trim() : "新的独立会话，等待输入任务";
        List<String> roleKeys = request.roleKeys() == null || request.roleKeys().isEmpty()
                ? List.of("primary", "review", "test")
                : request.roleKeys();
        String focusRole = StringUtils.hasText(request.focusRoleKey()) ? request.focusRoleKey().trim() : "primary";
        BootstrapResponse.ThreadView thread = createThreadInternal(
                folder.projectId(),
                folder.folderId(),
                label,
                summary,
                roleKeys,
                focusRole,
                "未编排",
                folder.path()
        );
        List<BootstrapResponse.MessageView> messages = List.of(
                insertMessage(folder.projectId(), thread.id(), "seed-thread-welcome", "agent", "主助手",
                        "已为 " + folder.name() + " 目录新建独立会话。这里会有自己的上下文、消息和后续 agent 接力记录。", "completed"),
                insertMessage(folder.projectId(), thread.id(), "seed-thread-review", "agent", "review-agent",
                        "你可以把这个会话当作一条新的分析线，不会覆盖同目录下其他会话。", "completed")
        );
        return new WorkspaceDtos.CreateThreadResponse(thread, messages);
    }

    @Override
    public Optional<BootstrapResponse.ThreadView> findThread(UUID threadId) {
        return threadViews("""
                        SELECT t.id, t.client_key, t.folder_id, f.name AS folder, t.label, t.summary,
                               t.focus_role_id, fr.client_key AS focus_role_key, t.role_status, t.status, t.metadata::text AS metadata,
                               sr.id AS session_role_id, sr.client_key AS session_role_key
                        FROM threads t
                        JOIN folders f ON f.id = t.folder_id
                        LEFT JOIN roles fr ON fr.id = t.focus_role_id
                        LEFT JOIN roles sr ON sr.client_key = t.metadata->>'session_role_key' AND sr.project_id = t.project_id
                        WHERE t.id = :thread_id AND t.status <> 'deleted'
                        """, Map.of("thread_id", threadId)).stream().findFirst();
    }

    @Override
    @Transactional
    public BootstrapResponse.ThreadView patchThread(UUID threadId, WorkspaceDtos.PatchThreadRequest request) {
        ThreadPatchTarget current = jdbcClient.sql("""
                        SELECT t.project_id, t.label, t.summary, t.status, t.metadata::text AS metadata,
                               fr.client_key AS focus_role_key
                        FROM threads t
                        LEFT JOIN roles fr ON fr.id = t.focus_role_id
                        WHERE t.id = :thread_id AND t.status <> 'deleted'
                        """)
                .param("thread_id", threadId)
                .query((rs, rowNum) -> new ThreadPatchTarget(
                        rs.getObject("project_id", UUID.class),
                        rs.getString("label"),
                        rs.getString("summary"),
                        rs.getString("status"),
                        rs.getString("focus_role_key"),
                        readJson(rs.getString("metadata"))
                ))
                .single();
        String status = StringUtils.hasText(request.status()) ? request.status().trim() : current.status();
        if (!List.of("active", "archived").contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status must be active or archived.");
        }
        String focusRoleKey = StringUtils.hasText(request.focusRoleKey()) ? request.focusRoleKey().trim() : current.focusRoleKey();
        UUID focusRoleId = roleId(current.projectId(), focusRoleKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "focus_role_key is not available."));
        ObjectNode metadata = current.metadata().isObject() ? (ObjectNode) current.metadata().deepCopy() : objectMapper.createObjectNode();
        metadata.put("focus_role_key", focusRoleKey);
        jdbcClient.sql("""
                        UPDATE threads
                        SET label = :label,
                            summary = :summary,
                            focus_role_id = :focus_role_id,
                            status = :status,
                            metadata = CAST(:metadata AS jsonb)
                        WHERE id = :thread_id
                        """)
                .param("thread_id", threadId)
                .param("label", StringUtils.hasText(request.label()) ? request.label().trim() : current.label())
                .param("summary", request.summary() == null ? current.summary() : request.summary().trim())
                .param("focus_role_id", focusRoleId)
                .param("status", status)
                .param("metadata", metadata.toString())
                .update();
        return findThread(threadId).orElseThrow();
    }

    @Override
    public List<BootstrapResponse.MessageView> listMessages(UUID threadId, int limit) {
        return jdbcClient.sql("""
                        SELECT id, client_message_id, role, agent_name, content, kind, status, created_at
                        FROM messages
                        WHERE thread_id = :thread_id
                        ORDER BY created_at
                        LIMIT :limit
                        """)
                .param("thread_id", threadId)
                .param("limit", limit)
                .query((rs, rowNum) -> new BootstrapResponse.MessageView(
                        rs.getObject("id", UUID.class),
                        rs.getString("client_message_id"),
                        rs.getString("role"),
                        rs.getString("agent_name"),
                        rs.getString("content"),
                        rs.getString("kind"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .list();
    }

    @Override
    @Transactional
    public WorkspaceDtos.SendMessageResponse sendMessage(
            UUID threadId,
            WorkspaceDtos.SendMessageRequest request,
            String idempotencyKey
    ) {
        UUID projectId = findProjectIdByThread(threadId).orElseThrow();
        String clientMessageId = StringUtils.hasText(request.clientMessageId())
                ? request.clientMessageId().trim()
                : (StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : "msg-" + UUID.randomUUID());
        insertMessageIfAbsent(projectId, threadId, clientMessageId, "user", null, request.content().trim(), "completed");
        insertMessageIfAbsent(projectId, threadId, clientMessageId + ":agent", "agent", "主助手",
                "Agent 编排任务已排队，等待 B09 SSE 接入后输出。", "pending");
        BootstrapResponse.MessageView userMessage = findMessage(threadId, clientMessageId).orElseThrow();
        BootstrapResponse.MessageView placeholder = findMessage(threadId, clientMessageId + ":agent").orElseThrow();
        return new WorkspaceDtos.SendMessageResponse(
                userMessage,
                placeholder,
                UUID.nameUUIDFromBytes((threadId + ":job:" + clientMessageId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "processing",
                "/api/v1/threads/" + threadId + "/stream"
        );
    }

    @Override
    @Transactional
    public BootstrapResponse.MessageView completeAgentMessage(UUID threadId, String clientMessageId, String content) {
        jdbcClient.sql("""
                        UPDATE messages
                        SET content = :content,
                            status = 'completed',
                            completed_at = :completed_at
                        WHERE thread_id = :thread_id
                          AND client_message_id = :client_message_id
                          AND role = 'agent'
                        """)
                .param("thread_id", threadId)
                .param("client_message_id", clientMessageId)
                .param("content", content)
                .param("completed_at", Timestamp.from(Instant.now()))
                .update();
        return findMessage(threadId, clientMessageId).orElseThrow();
    }

    private BootstrapResponse.ThreadView createThreadInternal(
            UUID projectId,
            UUID folderId,
            String label,
            String summary,
            List<String> roleKeys,
            String focusRoleKey,
            String roleStatus,
            String file
    ) {
        UUID focusRoleId = roleId(projectId, focusRoleKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "focus_role_key is not available."));
        String clientKey = "thread-" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("file", file);
        metadata.put("focus_role_key", focusRoleKey);
        metadata.put("session_role_key", BootstrapSeedData.sessionRoleKey(clientKey));
        metadata.set("role_keys", array(roleKeys));
        UUID threadId = jdbcClient.sql("""
                        INSERT INTO threads (project_id, folder_id, client_key, label, summary, focus_role_id, role_status, metadata)
                        VALUES (:project_id, :folder_id, :client_key, :label, :summary, :focus_role_id, :role_status, CAST(:metadata AS jsonb))
                        RETURNING id
                        """)
                .param("project_id", projectId)
                .param("folder_id", folderId)
                .param("client_key", clientKey)
                .param("label", label)
                .param("summary", summary)
                .param("focus_role_id", focusRoleId)
                .param("role_status", roleStatus)
                .param("metadata", metadata.toString())
                .query(UUID.class)
                .single();
        int sortOrder = 0;
        for (String roleKey : roleKeys) {
            int currentSortOrder = sortOrder;
            roleId(projectId, roleKey).ifPresent(roleId -> upsertThreadRole(
                    threadId,
                    roleId,
                    roleKey.equals(focusRoleKey),
                    roleStatus,
                    currentSortOrder
            ));
            sortOrder++;
        }
        return findThread(threadId).orElseThrow();
    }

    private BootstrapResponse.MessageView insertMessage(
            UUID projectId,
            UUID threadId,
            String clientMessageId,
            String role,
            String agentName,
            String content,
            String status
    ) {
        insertMessageIfAbsent(projectId, threadId, clientMessageId, role, agentName, content, status);
        return findMessage(threadId, clientMessageId).orElseThrow();
    }

    private void insertMessageIfAbsent(
            UUID projectId,
            UUID threadId,
            String clientMessageId,
            String role,
            String agentName,
            String content,
            String status
    ) {
        jdbcClient.sql("""
                        INSERT INTO messages (project_id, thread_id, client_message_id, role, agent_name, content, kind, status, completed_at)
                        VALUES (:project_id, :thread_id, :client_message_id, :role, :agent_name, :content, 'text', :status, :completed_at)
                        ON CONFLICT (thread_id, client_message_id) DO NOTHING
                        """)
                .param("project_id", projectId)
                .param("thread_id", threadId)
                .param("client_message_id", clientMessageId)
                .param("role", role)
                .param("agent_name", agentName)
                .param("content", content)
                .param("status", status)
                .param("completed_at", "completed".equals(status) ? Timestamp.from(Instant.now()) : null)
                .update();
    }

    private Optional<BootstrapResponse.MessageView> findMessage(UUID threadId, String clientMessageId) {
        return jdbcClient.sql("""
                        SELECT id, client_message_id, role, agent_name, content, kind, status, created_at
                        FROM messages
                        WHERE thread_id = :thread_id AND client_message_id = :client_message_id
                        """)
                .param("thread_id", threadId)
                .param("client_message_id", clientMessageId)
                .query((rs, rowNum) -> new BootstrapResponse.MessageView(
                        rs.getObject("id", UUID.class),
                        rs.getString("client_message_id"),
                        rs.getString("role"),
                        rs.getString("agent_name"),
                        rs.getString("content"),
                        rs.getString("kind"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }

    private List<WorkspaceDtos.FolderItem> folderItems(UUID projectId, boolean includeThreads) {
        List<FolderRow> folders = jdbcClient.sql("""
                        SELECT id, name, path, sort_order, created_at
                        FROM folders
                        WHERE project_id = :project_id
                        ORDER BY sort_order, created_at
                        """)
                .param("project_id", projectId)
                .query(this::folderRow)
                .list();
        return folders.stream()
                .map(folder -> folderItem(folder, includeThreads ? listThreads(folder.id()) : List.of(), includeThreads))
                .toList();
    }

    private WorkspaceDtos.FolderItem folderItem(FolderRow folder, List<BootstrapResponse.ThreadView> threads, boolean includeThreads) {
        return new WorkspaceDtos.FolderItem(
                folder.id(),
                folder.name(),
                folder.path(),
                folder.sortOrder(),
                includeThreads ? threads.size() : countThreads(folder.id()),
                includeThreads ? threads.stream().map(BootstrapResponse.ThreadView::clientKey).toList() : List.of(),
                includeThreads ? threads : null
        );
    }

    private int countThreads(UUID folderId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM threads WHERE folder_id = :folder_id AND status <> 'deleted'")
                .param("folder_id", folderId)
                .query(Integer.class)
                .single();
    }

    private List<BootstrapResponse.ThreadView> threadViews(String sql, Map<String, ?> params) {
        JdbcClient.StatementSpec spec = jdbcClient.sql(sql);
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }
        Map<UUID, BootstrapResponse.MessageView> latestMessages = latestMessages();
        return spec.query((rs, rowNum) -> {
            JsonNode metadata = readJson(rs.getString("metadata"));
            List<String> roleKeys = jsonTextArray(metadata.path("role_keys"));
            BootstrapResponse.MessageView latest = latestMessages.get(rs.getObject("id", UUID.class));
            return new BootstrapResponse.ThreadView(
                    rs.getObject("id", UUID.class),
                    rs.getString("client_key"),
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
                    latest == null ? null : latest.createdAt(),
                    latest == null ? null : latest.content(),
                    new BootstrapResponse.RagSummary(0, 0, null)
            );
        }).list();
    }

    private Map<UUID, BootstrapResponse.MessageView> latestMessages() {
        Map<UUID, BootstrapResponse.MessageView> result = new LinkedHashMap<>();
        jdbcClient.sql("""
                        SELECT DISTINCT ON (thread_id) thread_id, id, client_message_id, role, agent_name, content, kind, status, created_at
                        FROM messages
                        ORDER BY thread_id, created_at DESC
                        """)
                .query((rs, rowNum) -> {
                    result.put(rs.getObject("thread_id", UUID.class), new BootstrapResponse.MessageView(
                            rs.getObject("id", UUID.class),
                            rs.getString("client_message_id"),
                            rs.getString("role"),
                            rs.getString("agent_name"),
                            rs.getString("content"),
                            rs.getString("kind"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant()
                    ));
                    return null;
                })
                .list();
        return result;
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

    private Optional<UUID> roleId(UUID projectId, String roleKey) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM roles
                        WHERE project_id = :project_id AND client_key = :role_key
                        """)
                .param("project_id", projectId)
                .param("role_key", roleKey)
                .query(UUID.class)
                .optional();
    }

    private FolderProject folderProject(UUID folderId) {
        return jdbcClient.sql("""
                        SELECT id, project_id, name, path
                        FROM folders
                        WHERE id = :folder_id
                        """)
                .param("folder_id", folderId)
                .query((rs, rowNum) -> new FolderProject(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("path")
                ))
                .single();
    }

    private FolderRow folderRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new FolderRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("path"),
                rs.getInt("sort_order"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid workspace JSON value.", exception);
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

    private String normalizeName(String name) {
        return name.trim().replaceAll("/+$", "");
    }

    private record FolderRow(UUID id, String name, String path, int sortOrder, Instant createdAt) {
    }

    private record FolderProject(UUID folderId, UUID projectId, String name, String path) {
    }

    private record ThreadPatchTarget(
            UUID projectId,
            String label,
            String summary,
            String status,
            String focusRoleKey,
            JsonNode metadata
    ) {
    }
}
