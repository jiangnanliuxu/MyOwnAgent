package com.agentdesk.backend.mcp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface McpRepository {

    void ensureSeeded(UUID projectId);

    Optional<UUID> findProjectIdByEndpoint(UUID endpointId);

    List<McpDtos.EndpointItem> listEndpoints(UUID projectId);

    McpDtos.EndpointItem createEndpoint(UUID projectId, McpDtos.CreateEndpointRequest request);

    Optional<McpDtos.EndpointItem> findEndpoint(UUID endpointId);

    Optional<McpDtos.EndpointItem> findEndpoint(UUID projectId, String clientKey);

    McpDtos.EndpointItem patchEndpoint(UUID endpointId, McpDtos.PatchEndpointRequest request);

    McpDtos.HealthResult checkEndpoint(UUID endpointId, Integer timeoutMs);

    McpDtos.HealthCheckAllResponse checkAll(UUID projectId);

    McpDtos.HealthStatusResponse healthStatus(UUID projectId);

    List<McpDtos.ToolItem> toolRegistry(UUID projectId);

    void auditToolInvocation(UUID projectId, UUID endpointId, String toolName, String status, String message);
}
