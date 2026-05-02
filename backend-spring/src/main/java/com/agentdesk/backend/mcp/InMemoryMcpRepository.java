package com.agentdesk.backend.mcp;

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
public class InMemoryMcpRepository implements McpRepository {

    private final ObjectMapper objectMapper;
    private final Map<UUID, ProjectState> projects = new LinkedHashMap<>();
    private final Map<UUID, UUID> endpointProjects = new LinkedHashMap<>();

    public InMemoryMcpRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void ensureSeeded(UUID projectId) {
        projects.computeIfAbsent(projectId, this::seedProject);
    }

    @Override
    public synchronized Optional<UUID> findProjectIdByEndpoint(UUID endpointId) {
        return Optional.ofNullable(endpointProjects.get(endpointId));
    }

    @Override
    public synchronized List<McpDtos.EndpointItem> listEndpoints(UUID projectId) {
        ensureSeeded(projectId);
        return projects.get(projectId).endpoints.values().stream()
                .sorted(Comparator.comparing(EndpointState::createdAt))
                .map(this::endpointItem)
                .toList();
    }

    @Override
    public synchronized McpDtos.EndpointItem createEndpoint(UUID projectId, McpDtos.CreateEndpointRequest request) {
        ensureSeeded(projectId);
        ProjectState state = projects.get(projectId);
        String clientKey = StringUtils.hasText(request.clientKey()) ? request.clientKey().trim() : slug(request.name());
        if (state.endpoints.values().stream().anyMatch(endpoint -> clientKey.equals(endpoint.clientKey()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "MCP endpoint already exists.");
        }
        UUID endpointId = UUID.randomUUID();
        Instant now = Instant.now();
        EndpointState endpoint = new EndpointState(
                endpointId,
                clientKey,
                request.name().trim(),
                normalizeTransport(request.transport()),
                normalizeStatus(request.status(), "待连接"),
                StringUtils.hasText(request.authType()) ? request.authType().trim() : "local",
                request.url() == null ? null : request.url().trim(),
                objectOrEmpty(request.command()),
                normalizeTools(request.tools()),
                null,
                secretRef(projectId, endpointId, request.secretInput()),
                objectOrEmpty(request.healthConfig()),
                now,
                now
        );
        state.endpoints.put(endpoint.id(), endpoint);
        endpointProjects.put(endpoint.id(), projectId);
        return endpointItem(endpoint);
    }

    @Override
    public synchronized Optional<McpDtos.EndpointItem> findEndpoint(UUID endpointId) {
        UUID projectId = endpointProjects.get(endpointId);
        if (projectId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(projects.get(projectId).endpoints.get(endpointId)).map(this::endpointItem);
    }

    @Override
    public synchronized Optional<McpDtos.EndpointItem> findEndpoint(UUID projectId, String clientKey) {
        ensureSeeded(projectId);
        return projects.get(projectId).endpoints.values().stream()
                .filter(endpoint -> clientKey.equals(endpoint.clientKey()))
                .findFirst()
                .map(this::endpointItem);
    }

    @Override
    public synchronized McpDtos.EndpointItem patchEndpoint(UUID endpointId, McpDtos.PatchEndpointRequest request) {
        UUID projectId = endpointProjects.get(endpointId);
        ProjectState state = projects.get(projectId);
        EndpointState current = state.endpoints.get(endpointId);
        EndpointState updated = current.withPatch(
                valueOrCurrent(request.name(), current.name()),
                request.transport() == null ? current.transport() : normalizeTransport(request.transport()),
                normalizeStatus(request.status(), current.status()),
                valueOrCurrent(request.authType(), current.authType()),
                request.url() == null ? current.url() : request.url().trim(),
                request.command() == null ? current.command() : objectOrEmpty(request.command()),
                request.tools() == null ? current.tools() : normalizeTools(request.tools()),
                current.latencyMs(),
                StringUtils.hasText(request.secretInput()) ? secretRef(projectId, endpointId, request.secretInput()) : current.secretRef(),
                request.healthConfig() == null ? current.healthConfig() : objectOrEmpty(request.healthConfig()),
                Instant.now()
        );
        state.endpoints.put(endpointId, updated);
        return endpointItem(updated);
    }

    @Override
    public synchronized McpDtos.HealthResult checkEndpoint(UUID endpointId, Integer timeoutMs) {
        UUID projectId = endpointProjects.get(endpointId);
        ProjectState state = projects.get(projectId);
        EndpointState endpoint = state.endpoints.get(endpointId);
        Instant checkedAt = Instant.now();
        boolean stopped = "停用".equals(endpoint.status());
        Integer latencyMs = stopped ? null : deterministicLatency(endpoint, timeoutMs);
        String status = stopped ? "异常" : "已连接";
        EndpointState updated = endpoint.withPatch(endpoint.name(), endpoint.transport(), status, endpoint.authType(), endpoint.url(),
                endpoint.command(), endpoint.tools(), latencyMs, endpoint.secretRef(), endpoint.healthConfig(), checkedAt);
        state.endpoints.put(endpointId, updated);
        state.healthChecks.add(new HealthCheckState(endpointId, stopped ? "异常" : "正常", latencyMs,
                stopped ? "Endpoint is disabled." : "B08 placeholder health check passed.", checkedAt));
        return healthResult(updated, checkedAt, stopped);
    }

    @Override
    public synchronized McpDtos.HealthCheckAllResponse checkAll(UUID projectId) {
        ensureSeeded(projectId);
        List<McpDtos.HealthResult> results = listEndpoints(projectId).stream()
                .map(endpoint -> checkEndpoint(endpoint.id(), null))
                .toList();
        Instant checkedAt = results.stream().map(McpDtos.HealthResult::checkedAt).reduce((first, second) -> second).orElse(Instant.now());
        int connected = (int) results.stream().filter(result -> "已连接".equals(result.status())).count();
        return new McpDtos.HealthCheckAllResponse(results, new McpDtos.HealthSummary(results.size(), connected, results.size() - connected, checkedAt));
    }

    @Override
    public synchronized McpDtos.HealthStatusResponse healthStatus(UUID projectId) {
        List<McpDtos.EndpointItem> endpoints = listEndpoints(projectId);
        long connected = endpoints.stream().filter(endpoint -> "已连接".equals(endpoint.status())).count();
        String status = connected == endpoints.size() ? "正常" : connected == 0 ? "异常" : "待确认";
        return new McpDtos.HealthStatusResponse(List.of(
                List.of("MCP 连接", status, connected + " / " + endpoints.size() + " 个 endpoint 已连接。"),
                List.of("工具注册", endpoints.isEmpty() ? "待确认" : "正常", toolRegistry(projectId).size() + " 个工具已进入 Spring registry。"),
                List.of("调用治理", "占位", "B08 只做鉴权、限流预留和审计，不执行真实外部 MCP 工具。"),
                List.of("密钥状态", "待配置", "页面只展示 secret_ref 或脱敏摘要，真实密钥由 Spring 密钥服务托管。")
        ));
    }

    @Override
    public synchronized List<McpDtos.ToolItem> toolRegistry(UUID projectId) {
        return listEndpoints(projectId).stream()
                .flatMap(endpoint -> endpoint.tools().stream().map(tool -> new McpDtos.ToolItem(
                        endpoint.id(),
                        endpoint.clientKey(),
                        endpoint.name(),
                        endpoint.transport(),
                        endpoint.status(),
                        tool
                )))
                .toList();
    }

    @Override
    public synchronized void auditToolInvocation(UUID projectId, UUID endpointId, String toolName, String status, String message) {
        ensureSeeded(projectId);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("endpoint_id", endpointId.toString());
        metadata.put("tool_name", toolName);
        metadata.put("status", status);
        projects.get(projectId).auditLogs.add(new AuditLogState("mcp_tool_invoke", message, metadata, Instant.now()));
    }

    private ProjectState seedProject(UUID projectId) {
        ProjectState state = new ProjectState(projectId);
        Instant now = Instant.now();
        for (BootstrapSeedData.SeedMcpEndpoint seed : BootstrapSeedData.mcpEndpoints()) {
            EndpointState endpoint = new EndpointState(
                    stableId(projectId, "mcp:" + seed.clientKey()),
                    seed.clientKey(),
                    seed.name(),
                    seed.transport(),
                    seed.status(),
                    seed.authType(),
                    seed.url(),
                    objectMapper.createObjectNode(),
                    seed.tools(),
                    seed.latencyMs(),
                    null,
                    objectMapper.createObjectNode(),
                    now,
                    now
            );
            state.endpoints.put(endpoint.id(), endpoint);
            endpointProjects.put(endpoint.id(), projectId);
        }
        return state;
    }

    private McpDtos.EndpointItem endpointItem(EndpointState endpoint) {
        return new McpDtos.EndpointItem(
                endpoint.id(),
                endpoint.clientKey(),
                endpoint.name(),
                endpoint.transport(),
                endpoint.status(),
                endpoint.authType(),
                endpoint.authType(),
                endpoint.url(),
                endpoint.command(),
                endpoint.tools(),
                endpoint.latencyMs(),
                latency(endpoint.latencyMs()),
                endpoint.secretRef(),
                endpoint.healthConfig(),
                endpoint.createdAt(),
                endpoint.updatedAt()
        );
    }

    private McpDtos.HealthResult healthResult(EndpointState endpoint, Instant checkedAt, boolean stopped) {
        return new McpDtos.HealthResult(endpoint.id(), endpoint.name(), endpoint.status(), endpoint.latencyMs(), latency(endpoint.latencyMs()),
                stopped ? List.of() : endpoint.tools(), stopped ? "Endpoint is disabled." : null, checkedAt);
    }

    private int deterministicLatency(EndpointState endpoint, Integer timeoutMs) {
        int max = timeoutMs == null ? 180 : Math.max(30, Math.min(timeoutMs, 5000));
        int value = 30 + Math.abs(endpoint.id().hashCode() % 120);
        return Math.min(value, max);
    }

    private String secretRef(UUID projectId, UUID endpointId, String secretInput) {
        if (!StringUtils.hasText(secretInput)) {
            return null;
        }
        return "secret://project/" + projectId + "/mcp/" + (endpointId == null ? "pending" : endpointId) + "/credential";
    }

    private String normalizeTransport(String transport) {
        String value = transport == null ? "" : transport.trim();
        if (!List.of("stdio", "http", "streamable_http", "sse", "plugin api", "iab").contains(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "transport is not supported.");
        }
        return value;
    }

    private String normalizeStatus(String status, String fallback) {
        String value = StringUtils.hasText(status) ? status.trim() : fallback;
        if (!List.of("已连接", "待连接", "异常", "停用").contains(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status must be 已连接, 待连接, 异常, or 停用.");
        }
        return value;
    }

    private List<String> normalizeTools(List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    private JsonNode objectOrEmpty(JsonNode value) {
        if (value == null || value.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!value.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON value must be an object.");
        }
        return value;
    }

    private String valueOrCurrent(String value, String current) {
        return StringUtils.hasText(value) ? value.trim() : current;
    }

    private String latency(Integer latencyMs) {
        return latencyMs == null ? "未检查" : latencyMs + "ms";
    }

    private String slug(String value) {
        return value.trim().toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-").replaceAll("^-|-$", "");
    }

    private UUID stableId(UUID ownerId, String key) {
        return UUID.nameUUIDFromBytes((ownerId + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private record ProjectState(
            UUID projectId,
            Map<UUID, EndpointState> endpoints,
            List<HealthCheckState> healthChecks,
            List<AuditLogState> auditLogs
    ) {
        ProjectState(UUID projectId) {
            this(projectId, new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    private record EndpointState(
            UUID id,
            String clientKey,
            String name,
            String transport,
            String status,
            String authType,
            String url,
            JsonNode command,
            List<String> tools,
            Integer latencyMs,
            String secretRef,
            JsonNode healthConfig,
            Instant createdAt,
            Instant updatedAt
    ) {
        EndpointState withPatch(
                String name,
                String transport,
                String status,
                String authType,
                String url,
                JsonNode command,
                List<String> tools,
                Integer latencyMs,
                String secretRef,
                JsonNode healthConfig,
                Instant updatedAt
        ) {
            return new EndpointState(id, clientKey, name, transport, status, authType, url, command, tools, latencyMs, secretRef, healthConfig, createdAt, updatedAt);
        }
    }

    private record HealthCheckState(UUID endpointId, String status, Integer latencyMs, String message, Instant checkedAt) {
    }

    private record AuditLogState(String type, String message, JsonNode metadata, Instant createdAt) {
    }
}
