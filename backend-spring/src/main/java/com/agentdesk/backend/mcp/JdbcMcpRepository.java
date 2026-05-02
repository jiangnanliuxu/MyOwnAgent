package com.agentdesk.backend.mcp;

import com.agentdesk.backend.bootstrap.BootstrapRepository;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("dev")
public class JdbcMcpRepository implements McpRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final BootstrapRepository bootstrapRepository;

    public JdbcMcpRepository(JdbcClient jdbcClient, ObjectMapper objectMapper, BootstrapRepository bootstrapRepository) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.bootstrapRepository = bootstrapRepository;
    }

    @Override
    public void ensureSeeded(UUID projectId) {
        bootstrapRepository.ensureSeeded(projectId);
    }

    @Override
    public Optional<UUID> findProjectIdByEndpoint(UUID endpointId) {
        return jdbcClient.sql("SELECT project_id FROM mcp_endpoints WHERE id = :endpoint_id")
                .param("endpoint_id", endpointId)
                .query(UUID.class)
                .optional();
    }

    @Override
    public List<McpDtos.EndpointItem> listEndpoints(UUID projectId) {
        return jdbcClient.sql(endpointSelect() + " WHERE project_id = :project_id ORDER BY created_at")
                .param("project_id", projectId)
                .query(this::endpointItem)
                .list();
    }

    @Override
    @Transactional
    public McpDtos.EndpointItem createEndpoint(UUID projectId, McpDtos.CreateEndpointRequest request) {
        String clientKey = StringUtils.hasText(request.clientKey()) ? request.clientKey().trim() : slug(request.name());
        try {
            UUID id = jdbcClient.sql("""
                            INSERT INTO mcp_endpoints (project_id, client_key, name, transport, status, auth_type, url, command, tools, secret_ref, health_config)
                            VALUES (:project_id, :client_key, :name, :transport, :status, :auth_type, :url, CAST(:command AS jsonb), :tools, null, CAST(:health_config AS jsonb))
                            RETURNING id
                            """)
                    .param("project_id", projectId)
                    .param("client_key", clientKey)
                    .param("name", request.name().trim())
                    .param("transport", normalizeTransport(request.transport()))
                    .param("status", normalizeStatus(request.status(), "待连接"))
                    .param("auth_type", StringUtils.hasText(request.authType()) ? request.authType().trim() : "local")
                    .param("url", request.url() == null ? null : request.url().trim())
                    .param("command", objectOrEmpty(request.command()).toString())
                    .param("tools", normalizeTools(request.tools()).toArray(String[]::new))
                    .param("health_config", objectOrEmpty(request.healthConfig()).toString())
                    .query(UUID.class)
                    .single();
            if (StringUtils.hasText(request.secretInput())) {
                jdbcClient.sql("UPDATE mcp_endpoints SET secret_ref = :secret_ref WHERE id = :endpoint_id")
                        .param("endpoint_id", id)
                        .param("secret_ref", secretRef(projectId, id, request.secretInput()))
                        .update();
            }
            return findEndpoint(id).orElseThrow();
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "MCP endpoint already exists.");
        }
    }

    @Override
    public Optional<McpDtos.EndpointItem> findEndpoint(UUID endpointId) {
        return jdbcClient.sql(endpointSelect() + " WHERE id = :endpoint_id")
                .param("endpoint_id", endpointId)
                .query(this::endpointItem)
                .optional();
    }

    @Override
    public Optional<McpDtos.EndpointItem> findEndpoint(UUID projectId, String clientKey) {
        return jdbcClient.sql(endpointSelect() + " WHERE project_id = :project_id AND client_key = :client_key")
                .param("project_id", projectId)
                .param("client_key", clientKey)
                .query(this::endpointItem)
                .optional();
    }

    @Override
    @Transactional
    public McpDtos.EndpointItem patchEndpoint(UUID endpointId, McpDtos.PatchEndpointRequest request) {
        McpDtos.EndpointItem current = findEndpoint(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP endpoint is not available."));
        UUID projectId = findProjectIdByEndpoint(endpointId).orElseThrow();
        String secretRef = StringUtils.hasText(request.secretInput())
                ? secretRef(projectId, endpointId, request.secretInput())
                : current.secretRef();
        jdbcClient.sql("""
                        UPDATE mcp_endpoints
                        SET name = :name,
                            transport = :transport,
                            status = :status,
                            auth_type = :auth_type,
                            url = :url,
                            command = CAST(:command AS jsonb),
                            tools = :tools,
                            secret_ref = :secret_ref,
                            health_config = CAST(:health_config AS jsonb)
                        WHERE id = :endpoint_id
                        """)
                .param("endpoint_id", endpointId)
                .param("name", valueOrCurrent(request.name(), current.name()))
                .param("transport", request.transport() == null ? current.transport() : normalizeTransport(request.transport()))
                .param("status", normalizeStatus(request.status(), current.status()))
                .param("auth_type", valueOrCurrent(request.authType(), current.authType()))
                .param("url", request.url() == null ? current.url() : request.url().trim())
                .param("command", (request.command() == null ? current.command() : objectOrEmpty(request.command())).toString())
                .param("tools", (request.tools() == null ? current.tools() : normalizeTools(request.tools())).toArray(String[]::new))
                .param("secret_ref", secretRef)
                .param("health_config", (request.healthConfig() == null ? current.healthConfig() : objectOrEmpty(request.healthConfig())).toString())
                .update();
        return findEndpoint(endpointId).orElseThrow();
    }

    @Override
    @Transactional
    public McpDtos.HealthResult checkEndpoint(UUID endpointId, Integer timeoutMs) {
        McpDtos.EndpointItem endpoint = findEndpoint(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP endpoint is not available."));
        Instant checkedAt = Instant.now();
        boolean stopped = "停用".equals(endpoint.status());
        String endpointStatus = stopped ? "异常" : "已连接";
        String healthStatus = stopped ? "异常" : "正常";
        Integer latencyMs = stopped ? null : deterministicLatency(endpoint, timeoutMs);
        String message = stopped ? "Endpoint is disabled." : "B08 placeholder health check passed.";
        jdbcClient.sql("""
                        INSERT INTO mcp_health_checks (endpoint_id, status, latency_ms, message, checked_at)
                        VALUES (:endpoint_id, :status, :latency_ms, :message, :checked_at)
                        """)
                .param("endpoint_id", endpoint.id())
                .param("status", healthStatus)
                .param("latency_ms", latencyMs)
                .param("message", message)
                .param("checked_at", Timestamp.from(checkedAt))
                .update();
        jdbcClient.sql("UPDATE mcp_endpoints SET status = :status, latency_ms = :latency_ms WHERE id = :endpoint_id")
                .param("endpoint_id", endpoint.id())
                .param("status", endpointStatus)
                .param("latency_ms", latencyMs)
                .update();
        return new McpDtos.HealthResult(endpoint.id(), endpoint.name(), endpointStatus, latencyMs, latency(latencyMs),
                stopped ? List.of() : endpoint.tools(), stopped ? message : null, checkedAt);
    }

    @Override
    @Transactional
    public McpDtos.HealthCheckAllResponse checkAll(UUID projectId) {
        List<McpDtos.HealthResult> results = listEndpoints(projectId).stream()
                .map(endpoint -> checkEndpoint(endpoint.id(), null))
                .toList();
        Instant checkedAt = results.stream().map(McpDtos.HealthResult::checkedAt).reduce((first, second) -> second).orElse(Instant.now());
        int connected = (int) results.stream().filter(result -> "已连接".equals(result.status())).count();
        return new McpDtos.HealthCheckAllResponse(results, new McpDtos.HealthSummary(results.size(), connected, results.size() - connected, checkedAt));
    }

    @Override
    public McpDtos.HealthStatusResponse healthStatus(UUID projectId) {
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
    public List<McpDtos.ToolItem> toolRegistry(UUID projectId) {
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
    public void auditToolInvocation(UUID projectId, UUID endpointId, String toolName, String status, String message) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("endpoint_id", endpointId.toString());
        metadata.put("tool_name", toolName);
        metadata.put("status", status);
        jdbcClient.sql("""
                        INSERT INTO task_logs (project_id, type, level, message, metadata)
                        VALUES (:project_id, 'mcp_tool_invoke', 'info', :message, CAST(:metadata AS jsonb))
                        """)
                .param("project_id", projectId)
                .param("message", message)
                .param("metadata", metadata.toString())
                .update();
    }

    private String endpointSelect() {
        return """
                SELECT id, client_key, name, transport, status, auth_type, url, command::text AS command,
                       tools, latency_ms, secret_ref, health_config::text AS health_config, created_at, updated_at
                FROM mcp_endpoints
                """;
    }

    private McpDtos.EndpointItem endpointItem(ResultSet rs, int rowNum) throws SQLException {
        Integer latencyMs = rs.getObject("latency_ms", Integer.class);
        return new McpDtos.EndpointItem(
                rs.getObject("id", UUID.class),
                rs.getString("client_key"),
                rs.getString("name"),
                rs.getString("transport"),
                rs.getString("status"),
                rs.getString("auth_type"),
                rs.getString("auth_type"),
                rs.getString("url"),
                readJson(rs.getString("command")),
                textArray(rs.getArray("tools")),
                latencyMs,
                latency(latencyMs),
                rs.getString("secret_ref"),
                readJson(rs.getString("health_config")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private int deterministicLatency(McpDtos.EndpointItem endpoint, Integer timeoutMs) {
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

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid MCP JSON value.", exception);
        }
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

    private String valueOrCurrent(String value, String current) {
        return StringUtils.hasText(value) ? value.trim() : current;
    }

    private String latency(Integer latencyMs) {
        return latencyMs == null ? "未检查" : latencyMs + "ms";
    }

    private String slug(String value) {
        return value.trim().toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-").replaceAll("^-|-$", "");
    }
}
