package com.agentdesk.backend.role;

import com.agentdesk.backend.auth.AuthRepository;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.llm.LlmGateway;
import com.agentdesk.backend.security.AuthenticatedUser;
import com.agentdesk.backend.secret.InMemorySecretVault;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoleService {

    private final AuthRepository authRepository;
    private final RoleRepository roleRepository;
    private final LlmGateway llmGateway;
    private final InMemorySecretVault secretVault;
    private final ObjectMapper objectMapper;

    public RoleService(
            AuthRepository authRepository,
            RoleRepository roleRepository,
            LlmGateway llmGateway,
            InMemorySecretVault secretVault,
            ObjectMapper objectMapper
    ) {
        this.authRepository = authRepository;
        this.roleRepository = roleRepository;
        this.llmGateway = llmGateway;
        this.secretVault = secretVault;
        this.objectMapper = objectMapper;
    }

    public RoleDtos.RoleListResponse listRoles(
            AuthenticatedUser user,
            UUID projectId,
            boolean includeThreadRoles
    ) {
        assertProjectAccess(user, projectId);
        roleRepository.ensureSeeded(projectId);
        return new RoleDtos.RoleListResponse(roleRepository.listRoles(projectId, includeThreadRoles));
    }

    public RoleDtos.RoleResponse getRole(AuthenticatedUser user, UUID roleId) {
        UUID projectId = projectIdForRole(roleId);
        assertProjectAccess(user, projectId);
        return new RoleDtos.RoleResponse(roleRepository.findRole(roleId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Role is not available.")));
    }

    public RoleDtos.RoleUpdateResponse patchRole(
            AuthenticatedUser user,
            UUID roleId,
            RoleDtos.PatchRoleRequest request
    ) {
        UUID projectId = projectIdForRole(roleId);
        assertProjectAccess(user, projectId);
        return roleRepository.patchRole(roleId, request);
    }

    public RoleDtos.TestConnectionResponse testConnection(
            AuthenticatedUser user,
            UUID roleId,
            RoleDtos.TestConnectionRequest request
    ) {
        UUID projectId = projectIdForRole(roleId);
        assertProjectAccess(user, projectId);
        RoleDtos.RoleItem role = roleRepository.findRole(roleId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Role is not available."));
        JsonNode config = request == null || request.config() == null ? role.config() : request.config();
        long started = System.nanoTime();
        String provider = text(config, role.config(), "provider");
        String model = text(config, role.config(), "model");
        try {
            LlmGateway.ChatConfig chatConfig = chatConfig(config, role.config());
            LlmGateway.ChatTurn turn = llmGateway.chat(
                    chatConfig,
                    List.of(
                            Map.of("role", "system", "content", "You are a connection test endpoint. Reply with OK only."),
                            Map.of("role", "user", "content", "ping")
                    ),
                    List.of()
            );
            int latencyMs = latencyMs(started);
            String content = turn.content() == null ? "" : turn.content().trim();
            return new RoleDtos.TestConnectionResponse(
                    true,
                    "connected",
                    StringUtils.hasText(content) ? "模型接口已连通。" : "模型接口已连通，但响应内容为空。",
                    provider,
                    model,
                    latencyMs
            );
        } catch (RuntimeException exception) {
            return new RoleDtos.TestConnectionResponse(
                    false,
                    "failed",
                    safeMessage(exception),
                    provider,
                    model,
                    latencyMs(started)
            );
        }
    }

    public RoleDtos.SyncRolesResponse syncFolderRoles(AuthenticatedUser user, UUID folderId) {
        UUID projectId = roleRepository.findProjectIdByFolder(folderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Folder is not available."));
        assertProjectAccess(user, projectId);
        roleRepository.ensureSeeded(projectId);
        return roleRepository.syncFolderRoles(folderId);
    }

    private UUID projectIdForRole(UUID roleId) {
        return roleRepository.findProjectIdByRole(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Role is not available."));
    }

    private void assertProjectAccess(AuthenticatedUser user, UUID projectId) {
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        if (!authRepository.projectBelongsToUser(user.id(), projectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Project is not available.");
        }
    }

    private LlmGateway.ChatConfig chatConfig(JsonNode config, JsonNode storedConfig) {
        String endpoint = text(config, storedConfig, "endpoint");
        String model = text(config, storedConfig, "model");
        String apiFormat = text(config, storedConfig, "api_format");
        String apiKey = text(config, null, "api_key");
        if (!StringUtils.hasText(apiKey)) {
            String secretRef = text(config, storedConfig, "secret_ref");
            apiKey = secretVault.resolve(secretRef)
                    .or(() -> java.util.Optional.ofNullable(System.getenv("AGENT_DESK_LLM_DEFAULT_API_KEY")).filter(StringUtils::hasText))
                    .or(() -> java.util.Optional.ofNullable(System.getenv("SILICONFLOW_API_KEY")).filter(StringUtils::hasText))
                    .orElse("");
        }
        return new LlmGateway.ChatConfig(endpoint, apiKey, model, apiFormat, temperature(config), maxTokens(config, 64));
    }

    private String text(JsonNode config, JsonNode fallback, String key) {
        String value = config == null ? "" : config.path(key).asText("");
        if (StringUtils.hasText(value)) {
            return value;
        }
        return fallback == null ? "" : fallback.path(key).asText("");
    }

    private double temperature(JsonNode config) {
        JsonNode parsed = configJson(config);
        if (parsed != null && parsed.has("temperature")) {
            return Math.min(Math.max(parsed.path("temperature").asDouble(0.2), 0), 2);
        }
        return 0.2;
    }

    private Integer maxTokens(JsonNode config, int fallback) {
        JsonNode parsed = configJson(config);
        if (parsed != null && parsed.has("max_tokens")) {
            int value = parsed.path("max_tokens").asInt(0);
            return value > 0 ? Math.min(value, 8192) : fallback;
        }
        return fallback;
    }

    private JsonNode configJson(JsonNode config) {
        String raw = text(config, null, "config_json");
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (IOException ignored) {
            return null;
        }
    }

    private int latencyMs(long started) {
        return (int) Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return "模型连接测试失败，请检查请求地址、API 格式、模型名和 API Key。";
        }
        return message.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
    }
}
