package com.agentdesk.backend.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!dev")
public class InMemoryBootstrapRepository implements BootstrapRepository {

    private static final Instant BASE_TIME = Instant.parse("2026-05-02T02:00:00Z");

    private final ObjectMapper objectMapper;

    public InMemoryBootstrapRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void ensureSeeded(UUID projectId) {
        // Default profile builds bootstrap data from immutable seed records.
    }

    @Override
    public Optional<BootstrapResponse.ProjectView> findProject(UUID projectId) {
        return Optional.of(new BootstrapResponse.ProjectView(projectId, "默认工作区", null));
    }

    @Override
    public BootstrapResponse load(UUID projectId, String activeThreadKey, com.fasterxml.jackson.databind.JsonNode preferences) {
        Map<String, BootstrapResponse.RoleView> roles = roles(projectId);
        List<BootstrapResponse.FolderView> folders = folders(projectId);
        Map<String, BootstrapResponse.ThreadView> threads = threads(projectId, roles);
        return new BootstrapResponse(
                new BootstrapResponse.ProjectView(projectId, "默认工作区", null),
                activeThreadKey,
                mergeDefaultPreferences(preferences),
                folders,
                threads,
                recentMessages(projectId),
                roles,
                skills(projectId),
                mcpEndpoints(projectId),
                BootstrapSeedData.healthItems()
        );
    }

    @Override
    public boolean threadKeyBelongsToProject(UUID projectId, String threadKey) {
        return BootstrapSeedData.threads().stream().anyMatch(thread -> thread.clientKey().equals(threadKey));
    }

    private List<BootstrapResponse.FolderView> folders(UUID projectId) {
        return List.of(
                folder(projectId, "src/auth", 0, List.of("session-review", "session-auth")),
                folder(projectId, "src/router", 1, List.of("route-primary", "route-review", "route-test")),
                folder(projectId, "tests", 2, List.of("login-test", "login-snapshot"))
        );
    }

    private BootstrapResponse.FolderView folder(UUID projectId, String name, int sortOrder, List<String> threadKeys) {
        return new BootstrapResponse.FolderView(
                stableId(projectId, "folder:" + name),
                name,
                name,
                sortOrder,
                threadKeys.size(),
                threadKeys
        );
    }

    private Map<String, BootstrapResponse.ThreadView> threads(UUID projectId, Map<String, BootstrapResponse.RoleView> roles) {
        Map<String, BootstrapResponse.ThreadView> result = new LinkedHashMap<>();
        for (BootstrapSeedData.SeedThread thread : BootstrapSeedData.threads()) {
            UUID sessionRoleId = stableId(projectId, "role:" + BootstrapSeedData.sessionRoleKey(thread.clientKey()));
            result.put(thread.clientKey(), new BootstrapResponse.ThreadView(
                    stableId(projectId, "thread:" + thread.clientKey()),
                    thread.clientKey(),
                    stableId(projectId, "folder:" + thread.folder()),
                    thread.folder(),
                    thread.file(),
                    thread.label(),
                    thread.summary(),
                    roles.get(thread.focusRole()).id(),
                    thread.focusRole(),
                    thread.roles(),
                    thread.roles(),
                    thread.roleStatus(),
                    sessionRoleId,
                    BootstrapSeedData.sessionRoleKey(thread.clientKey()),
                    "active",
                    BASE_TIME.plusSeconds(thread.clientKey().length() * 60L),
                    BootstrapSeedData.messages(thread).getLast().content(),
                    new BootstrapResponse.RagSummary(0, 0, null)
            ));
        }
        return result;
    }

    private Map<String, List<BootstrapResponse.MessageView>> recentMessages(UUID projectId) {
        Map<String, List<BootstrapResponse.MessageView>> result = new LinkedHashMap<>();
        for (BootstrapSeedData.SeedThread thread : BootstrapSeedData.threads()) {
            result.put(thread.clientKey(), BootstrapSeedData.messages(thread).stream()
                    .map(message -> new BootstrapResponse.MessageView(
                            stableId(projectId, "message:" + thread.clientKey() + ":" + message.sortOrder()),
                            "seed-" + message.sortOrder(),
                            message.role(),
                            "agent".equals(message.role()) ? message.title() : null,
                            message.content(),
                            "text",
                            "completed",
                            BASE_TIME.plusSeconds(message.sortOrder())
                    ))
                    .toList());
        }
        return result;
    }

    private Map<String, BootstrapResponse.RoleView> roles(UUID projectId) {
        Map<String, BootstrapResponse.RoleView> result = new LinkedHashMap<>();
        for (BootstrapSeedData.SeedRole role : BootstrapSeedData.roles(objectMapper)) {
            result.put(role.clientKey(), roleView(projectId, role));
        }
        for (BootstrapSeedData.SeedThread thread : BootstrapSeedData.threads()) {
            BootstrapSeedData.SeedRole sessionRole = sessionRole(thread);
            result.put(sessionRole.clientKey(), roleView(projectId, sessionRole));
        }
        return result;
    }

    private BootstrapResponse.RoleView roleView(UUID projectId, BootstrapSeedData.SeedRole role) {
        return new BootstrapResponse.RoleView(
                stableId(projectId, "role:" + role.clientKey()),
                role.clientKey(),
                role.name(),
                role.alias(),
                role.tag(),
                role.description(),
                role.shortDescription(),
                "active",
                role.builtin(),
                role.config()
        );
    }

    private BootstrapSeedData.SeedRole sessionRole(BootstrapSeedData.SeedThread thread) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("model", "GPT-5.4-mini");
        config.put("provider", "OpenAI");
        config.put("compression", "轻压缩");
        config.put("prompt_prefix", "围绕 " + thread.folder() + " 目录的 " + thread.label() + " 会话处理任务。");
        config.put("source_thread_id", thread.clientKey());
        config.put("source_file", thread.file());
        config.put("source_folder", thread.folder());
        config.put("role_status", thread.roleStatus());
        return new BootstrapSeedData.SeedRole(
                BootstrapSeedData.sessionRoleKey(thread.clientKey()),
                thread.label(),
                thread.label() + "-session",
                "Session Role",
                thread.summary(),
                thread.folder() + " / " + thread.roleStatus(),
                false,
                config
        );
    }

    private List<BootstrapResponse.SkillView> skills(UUID projectId) {
        return BootstrapSeedData.skills(objectMapper).stream()
                .map(skill -> new BootstrapResponse.SkillView(
                        stableId(projectId, "skill:" + skill.clientKey()),
                        skill.clientKey(),
                        skill.name(),
                        skill.source(),
                        skill.status(),
                        skill.scope(),
                        skill.mounts(),
                        null,
                        skill.lastRun(),
                        skill.manifest(),
                        skill.config()
                ))
                .toList();
    }

    private List<BootstrapResponse.McpEndpointView> mcpEndpoints(UUID projectId) {
        return BootstrapSeedData.mcpEndpoints().stream()
                .map(endpoint -> new BootstrapResponse.McpEndpointView(
                        stableId(projectId, "mcp:" + endpoint.clientKey()),
                        endpoint.clientKey(),
                        endpoint.name(),
                        endpoint.transport(),
                        endpoint.status(),
                        endpoint.authType(),
                        endpoint.authType(),
                        endpoint.url(),
                        endpoint.tools(),
                        endpoint.latencyMs(),
                        endpoint.latencyMs() + "ms"
                ))
                .toList();
    }

    private ObjectNode mergeDefaultPreferences(com.fasterxml.jackson.databind.JsonNode storedPreferences) {
        ObjectNode preferences = objectMapper.createObjectNode();
        preferences.put("layout_density", "紧凑");
        preferences.put("execution_guard", "开启");
        preferences.put("composer_mode", "context_first");
        if (storedPreferences != null && storedPreferences.isObject()) {
            storedPreferences.properties().forEach(entry -> preferences.set(entry.getKey(), entry.getValue()));
        }
        return preferences;
    }

    private UUID stableId(UUID projectId, String key) {
        return UUID.nameUUIDFromBytes((projectId + ":" + key).getBytes(StandardCharsets.UTF_8));
    }
}
