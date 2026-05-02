package com.agentdesk.backend.mcp;

import com.agentdesk.backend.auth.AuthRepository;
import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
public class McpService {

    private final AuthRepository authRepository;
    private final McpRepository mcpRepository;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public McpService(
            AuthRepository authRepository,
            McpRepository mcpRepository,
            ObjectMapper objectMapper,
            @Value("${agent-desk.internal.token:local-dev-internal-token}") String internalToken
    ) {
        this.authRepository = authRepository;
        this.mcpRepository = mcpRepository;
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
    }

    public McpDtos.EndpointListResponse listEndpoints(AuthenticatedUser user, UUID projectId) {
        assertProjectAccess(user, projectId);
        mcpRepository.ensureSeeded(projectId);
        return new McpDtos.EndpointListResponse(mcpRepository.listEndpoints(projectId));
    }

    public McpDtos.EndpointResponse createEndpoint(
            AuthenticatedUser user,
            UUID projectId,
            McpDtos.CreateEndpointRequest request
    ) {
        assertProjectAccess(user, projectId);
        mcpRepository.ensureSeeded(projectId);
        return new McpDtos.EndpointResponse(mcpRepository.createEndpoint(projectId, request));
    }

    public McpDtos.EndpointResponse getEndpoint(AuthenticatedUser user, UUID endpointId) {
        UUID projectId = projectIdForEndpoint(endpointId);
        assertProjectAccess(user, projectId);
        return new McpDtos.EndpointResponse(mcpRepository.findEndpoint(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP endpoint is not available.")));
    }

    public McpDtos.EndpointResponse patchEndpoint(
            AuthenticatedUser user,
            UUID endpointId,
            McpDtos.PatchEndpointRequest request
    ) {
        UUID projectId = projectIdForEndpoint(endpointId);
        assertProjectAccess(user, projectId);
        return new McpDtos.EndpointResponse(mcpRepository.patchEndpoint(endpointId, request));
    }

    public McpDtos.HealthCheckResponse checkEndpoint(
            AuthenticatedUser user,
            UUID endpointId,
            McpDtos.HealthCheckRequest request
    ) {
        UUID projectId = projectIdForEndpoint(endpointId);
        assertProjectAccess(user, projectId);
        return new McpDtos.HealthCheckResponse(mcpRepository.checkEndpoint(endpointId, request == null ? null : request.timeoutMs()));
    }

    public McpDtos.HealthCheckAllResponse checkAll(AuthenticatedUser user, UUID projectId) {
        assertProjectAccess(user, projectId);
        mcpRepository.ensureSeeded(projectId);
        return mcpRepository.checkAll(projectId);
    }

    public McpDtos.HealthStatusResponse healthStatus(AuthenticatedUser user, UUID projectId) {
        assertProjectAccess(user, projectId);
        mcpRepository.ensureSeeded(projectId);
        return mcpRepository.healthStatus(projectId);
    }

    public McpDtos.ToolRegistryResponse toolRegistry(AuthenticatedUser user, UUID projectId) {
        assertProjectAccess(user, projectId);
        mcpRepository.ensureSeeded(projectId);
        return new McpDtos.ToolRegistryResponse(mcpRepository.toolRegistry(projectId));
    }

    public McpDtos.ToolInvokeResponse invokeTool(String token, McpDtos.ToolInvokeRequest request) {
        if (!StringUtils.hasText(token) || !token.equals(internalToken)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Internal token is required.");
        }
        if (request.projectId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "project_id is required.");
        }
        mcpRepository.ensureSeeded(request.projectId());
        McpDtos.EndpointItem endpoint = resolveEndpoint(request);
        if (!endpoint.tools().contains(request.toolName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Tool is not registered on endpoint.");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("mode", "placeholder");
        result.put("message", "B08 only audits tool invocation. Real MCP transport adapter is implemented later.");
        result.set("arguments", request.arguments() == null ? objectMapper.createObjectNode() : request.arguments());
        UUID invocationId = UUID.nameUUIDFromBytes((endpoint.id() + ":" + request.toolName() + ":" + Instant.now()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mcpRepository.auditToolInvocation(request.projectId(), endpoint.id(), request.toolName(), "placeholder", "Tool invocation audited without external execution.");
        return new McpDtos.ToolInvokeResponse(invocationId, "placeholder", endpoint.name(), request.toolName(), result, Instant.now());
    }

    private McpDtos.EndpointItem resolveEndpoint(McpDtos.ToolInvokeRequest request) {
        if (request.endpointId() != null) {
            McpDtos.EndpointItem endpoint = mcpRepository.findEndpoint(request.endpointId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP endpoint is not available."));
            UUID endpointProjectId = mcpRepository.findProjectIdByEndpoint(endpoint.id()).orElse(null);
            if (!request.projectId().equals(endpointProjectId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "MCP endpoint is not available.");
            }
            return endpoint;
        }
        if (StringUtils.hasText(request.endpointClientKey())) {
            return mcpRepository.findEndpoint(request.projectId(), request.endpointClientKey().trim())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP endpoint is not available."));
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "endpoint_id or endpoint_client_key is required.");
    }

    private UUID projectIdForEndpoint(UUID endpointId) {
        return mcpRepository.findProjectIdByEndpoint(endpointId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP endpoint is not available."));
    }

    private void assertProjectAccess(AuthenticatedUser user, UUID projectId) {
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        if (!authRepository.projectBelongsToUser(user.id(), projectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Project is not available.");
        }
    }
}
