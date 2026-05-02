package com.agentdesk.backend.role;

import com.agentdesk.backend.bootstrap.BootstrapSeedData;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!dev")
public class InMemoryRoleRepository implements RoleRepository {

    private final ObjectMapper objectMapper;
    private final Map<UUID, ProjectState> projects = new LinkedHashMap<>();
    private final Map<UUID, UUID> roleProjects = new LinkedHashMap<>();
    private final Map<UUID, UUID> folderProjects = new LinkedHashMap<>();

    public InMemoryRoleRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void ensureSeeded(UUID projectId) {
        projects.computeIfAbsent(projectId, this::seedProject);
    }

    @Override
    public synchronized Optional<UUID> findProjectIdByRole(UUID roleId) {
        return Optional.ofNullable(roleProjects.get(roleId));
    }

    @Override
    public synchronized Optional<UUID> findProjectIdByFolder(UUID folderId) {
        return Optional.ofNullable(folderProjects.get(folderId));
    }

    @Override
    public synchronized List<RoleDtos.RoleItem> listRoles(UUID projectId, boolean includeThreadRoles) {
        ensureSeeded(projectId);
        ProjectState state = projects.get(projectId);
        return state.roles.values().stream()
                .filter(role -> !"archived".equals(role.status()))
                .sorted(Comparator.comparing(RoleState::builtin).reversed().thenComparing(RoleState::createdAt))
                .map(role -> roleItem(state, role, includeThreadRoles))
                .toList();
    }

    @Override
    public synchronized Optional<RoleDtos.RoleItem> findRole(UUID roleId, boolean includeThreadRoles) {
        UUID projectId = roleProjects.get(roleId);
        if (projectId == null) {
            return Optional.empty();
        }
        ProjectState state = projects.get(projectId);
        return Optional.ofNullable(state.roles.get(roleId)).map(role -> roleItem(state, role, includeThreadRoles));
    }

    @Override
    public synchronized RoleDtos.RoleUpdateResponse patchRole(UUID roleId, RoleDtos.PatchRoleRequest request) {
        UUID projectId = roleProjects.get(roleId);
        ProjectState state = projects.get(projectId);
        RoleState current = state.roles.get(roleId);
        String status = StringUtils.hasText(request.status()) ? request.status().trim() : current.status();
        if (!List.of("active", "disabled", "archived").contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status must be active, disabled, or archived.");
        }
        JsonNode nextConfig = mergeConfig(current.config(), request.config(), projectId, roleId);
        RoleState updated = current.withPatch(
                valueOrCurrent(request.name(), current.name()),
                valueOrCurrent(request.alias(), current.alias()),
                valueOrCurrent(request.tag(), current.tag()),
                request.description() == null ? current.description() : request.description().trim(),
                request.shortDescription() == null ? current.shortDescription() : request.shortDescription().trim(),
                status,
                nextConfig
        );
        state.roles.put(roleId, updated);
        List<RoleDtos.SyncedThread> syncedThreads = syncSourceThread(
                state,
                nextConfig,
                StringUtils.hasText(request.name()),
                request.description() != null,
                updated.name(),
                updated.description()
        );
        return new RoleDtos.RoleUpdateResponse(roleItem(state, updated, true), syncedThreads);
    }

    @Override
    public synchronized RoleDtos.SyncRolesResponse syncFolderRoles(UUID folderId) {
        UUID projectId = folderProjects.get(folderId);
        ProjectState state = projects.get(projectId);
        FolderState folder = state.folders.get(folderId);
        int created = 0;
        int synced = 0;
        List<ThreadState> threads = state.threads.values().stream()
                .filter(thread -> thread.folderId().equals(folderId))
                .sorted(Comparator.comparing(ThreadState::createdAt))
                .toList();

        for (ThreadState thread : threads) {
            String sessionRoleKey = BootstrapSeedData.sessionRoleKey(thread.clientKey());
            if (state.roles.values().stream().noneMatch(role -> sessionRoleKey.equals(role.clientKey()))) {
                RoleState role = sessionRole(state, thread, sessionRoleKey);
                state.roles.put(role.id(), role);
                roleProjects.put(role.id(), projectId);
                created++;
            }
            state.threadRoles.entrySet().removeIf(entry -> entry.getValue().threadId().equals(thread.id()) && entry.getValue().focus());
            int sortOrder = 0;
            for (String roleKey : roleKeysWithSession(thread.roleKeys(), sessionRoleKey)) {
                Optional<RoleState> role = roleByClientKey(state, roleKey);
                if (role.isPresent()) {
                    ThreadRoleState threadRole = new ThreadRoleState(
                            thread.id(),
                            role.get().id(),
                            roleKey.equals(thread.focusRoleKey()),
                            thread.roleStatus(),
                            sortOrder++
                    );
                    state.threadRoles.put(thread.id() + ":" + role.get().id(), threadRole);
                    synced++;
                }
            }
        }

        return new RoleDtos.SyncRolesResponse(folder.id(), folder.name(), threadRoles(state, folderId), created, synced);
    }

    private ProjectState seedProject(UUID projectId) {
        ProjectState state = new ProjectState(projectId);
        int folderSort = 0;
        for (String folderName : List.of("src/auth", "src/router", "tests")) {
            FolderState folder = new FolderState(stableId(projectId, "folder:" + folderName), folderName, folderName, folderSort++, Instant.now());
            state.folders.put(folder.id(), folder);
            folderProjects.put(folder.id(), projectId);
        }
        Map<String, FolderState> foldersByName = new LinkedHashMap<>();
        state.folders.values().forEach(folder -> foldersByName.put(folder.name(), folder));

        for (BootstrapSeedData.SeedRole seedRole : BootstrapSeedData.roles(objectMapper)) {
            RoleState role = new RoleState(
                    stableId(projectId, "role:" + seedRole.clientKey()),
                    seedRole.clientKey(),
                    seedRole.name(),
                    seedRole.alias(),
                    seedRole.tag(),
                    seedRole.description(),
                    seedRole.shortDescription(),
                    "active",
                    seedRole.builtin(),
                    seedRole.config(),
                    Instant.now()
            );
            state.roles.put(role.id(), role);
            roleProjects.put(role.id(), projectId);
        }

        int threadSort = 0;
        for (BootstrapSeedData.SeedThread seedThread : BootstrapSeedData.threads()) {
            ThreadState thread = new ThreadState(
                    stableId(projectId, "thread:" + seedThread.clientKey()),
                    foldersByName.get(seedThread.folder()).id(),
                    seedThread.clientKey(),
                    seedThread.label(),
                    seedThread.file(),
                    seedThread.summary(),
                    seedThread.roles(),
                    seedThread.focusRole(),
                    seedThread.roleStatus(),
                    Instant.now().plusSeconds(threadSort++)
            );
            state.threads.put(thread.id(), thread);
            RoleState sessionRole = sessionRole(state, thread, BootstrapSeedData.sessionRoleKey(thread.clientKey()));
            state.roles.put(sessionRole.id(), sessionRole);
            roleProjects.put(sessionRole.id(), projectId);
        }
        for (ThreadState thread : state.threads.values()) {
            int sortOrder = 0;
            String sessionRoleKey = BootstrapSeedData.sessionRoleKey(thread.clientKey());
            for (String roleKey : roleKeysWithSession(thread.roleKeys(), sessionRoleKey)) {
                int currentSortOrder = sortOrder;
                roleByClientKey(state, roleKey).ifPresent(role -> state.threadRoles.put(
                        thread.id() + ":" + role.id(),
                        new ThreadRoleState(thread.id(), role.id(), roleKey.equals(thread.focusRoleKey()), thread.roleStatus(), currentSortOrder)
                ));
                sortOrder++;
            }
        }
        return state;
    }

    private RoleState sessionRole(ProjectState state, ThreadState thread, String sessionRoleKey) {
        boolean arranged = !"未编排".equals(thread.roleStatus());
        ObjectNode config = objectMapper.createObjectNode();
        config.put("model", arranged ? "GPT-5.4-mini" : "待编排");
        config.put("provider", arranged ? "OpenAI" : "未选择");
        config.put("compression", arranged ? "轻压缩" : "关闭压缩");
        config.put("source_thread_id", thread.clientKey());
        config.put("source_file", thread.file());
        config.put("source_folder", folderById(state, thread.folderId()).name());
        config.put("role_status", thread.roleStatus());
        config.put("matrix_copy", arranged
                ? "绑定 " + folderById(state, thread.folderId()).name() + " 目录下的 " + thread.label() + " 会话。"
                : "新增自 " + folderById(state, thread.folderId()).name() + " 目录，尚未进入正式角色编排。");
        return new RoleState(
                stableId(state.projectId(), "role:" + sessionRoleKey),
                sessionRoleKey,
                thread.label(),
                thread.label() + "-session",
                arranged ? "Session Role" : "未编排角色",
                thread.summary(),
                folderById(state, thread.folderId()).name() + " / " + thread.roleStatus(),
                "active",
                false,
                config,
                Instant.now()
        );
    }

    private RoleDtos.RoleItem roleItem(ProjectState state, RoleState role, boolean includeThreadRoles) {
        return new RoleDtos.RoleItem(
                role.id(),
                role.clientKey(),
                role.name(),
                role.alias(),
                role.tag(),
                role.description(),
                role.shortDescription(),
                role.status(),
                role.builtin(),
                role.config(),
                includeThreadRoles ? boundThreads(state, role.id()) : List.of()
        );
    }

    private List<RoleDtos.BoundThread> boundThreads(ProjectState state, UUID roleId) {
        return state.threadRoles.values().stream()
                .filter(threadRole -> threadRole.roleId().equals(roleId))
                .sorted(Comparator.comparing(ThreadRoleState::sortOrder))
                .map(threadRole -> {
                    ThreadState thread = state.threads.get(threadRole.threadId());
                    FolderState folder = state.folders.get(thread.folderId());
                    return new RoleDtos.BoundThread(
                            thread.id(),
                            thread.clientKey(),
                            thread.label(),
                            folder.id(),
                            folder.name(),
                            threadRole.focus(),
                            threadRole.status(),
                            threadRole.sortOrder()
                    );
                })
                .toList();
    }

    private List<RoleDtos.ThreadRoleItem> threadRoles(ProjectState state, UUID folderId) {
        return state.threadRoles.values().stream()
                .map(threadRole -> {
                    ThreadState thread = state.threads.get(threadRole.threadId());
                    RoleState role = state.roles.get(threadRole.roleId());
                    if (thread == null || role == null || !thread.folderId().equals(folderId)) {
                        return null;
                    }
                    return new RoleDtos.ThreadRoleItem(
                            thread.id(),
                            thread.clientKey(),
                            thread.label(),
                            role.id(),
                            role.clientKey(),
                            role.name(),
                            threadRole.focus(),
                            threadRole.status(),
                            threadRole.sortOrder()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(RoleDtos.ThreadRoleItem::threadClientKey).thenComparing(RoleDtos.ThreadRoleItem::sortOrder))
                .toList();
    }

    private List<RoleDtos.SyncedThread> syncSourceThread(
            ProjectState state,
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
        List<RoleDtos.SyncedThread> syncedThreads = new ArrayList<>();
        for (ThreadState thread : state.threads.values()) {
            if (thread.clientKey().equals(sourceThreadId) || thread.id().toString().equals(sourceThreadId)) {
                state.threads.put(thread.id(), thread.withPatch(
                        updateLabel ? nextLabel : thread.label(),
                        updateSummary ? nextSummary : thread.summary()
                ));
                syncedThreads.add(new RoleDtos.SyncedThread(thread.id(), thread.clientKey(), updateLabel, updateSummary));
            }
        }
        return syncedThreads;
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

    private Optional<RoleState> roleByClientKey(ProjectState state, String roleKey) {
        return state.roles.values().stream().filter(role -> roleKey.equals(role.clientKey())).findFirst();
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

    private FolderState folderById(ProjectState state, UUID folderId) {
        return state.folders.get(folderId);
    }

    private String valueOrCurrent(String value, String current) {
        return StringUtils.hasText(value) ? value.trim() : current;
    }

    private UUID stableId(UUID ownerId, String key) {
        return UUID.nameUUIDFromBytes((ownerId + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private record ProjectState(
            UUID projectId,
            Map<UUID, FolderState> folders,
            Map<UUID, ThreadState> threads,
            Map<UUID, RoleState> roles,
            Map<String, ThreadRoleState> threadRoles
    ) {
        ProjectState(UUID projectId) {
            this(projectId, new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    private record FolderState(UUID id, String name, String path, int sortOrder, Instant createdAt) {
    }

    private record ThreadState(
            UUID id,
            UUID folderId,
            String clientKey,
            String label,
            String file,
            String summary,
            List<String> roleKeys,
            String focusRoleKey,
            String roleStatus,
            Instant createdAt
    ) {
        ThreadState withPatch(String label, String summary) {
            return new ThreadState(id, folderId, clientKey, label, file, summary, roleKeys, focusRoleKey, roleStatus, createdAt);
        }
    }

    private record RoleState(
            UUID id,
            String clientKey,
            String name,
            String alias,
            String tag,
            String description,
            String shortDescription,
            String status,
            boolean builtin,
            JsonNode config,
            Instant createdAt
    ) {
        RoleState withPatch(
                String name,
                String alias,
                String tag,
                String description,
                String shortDescription,
                String status,
                JsonNode config
        ) {
            return new RoleState(id, clientKey, name, alias, tag, description, shortDescription, status, builtin, config, createdAt);
        }
    }

    private record ThreadRoleState(UUID threadId, UUID roleId, boolean focus, String status, int sortOrder) {
    }
}
