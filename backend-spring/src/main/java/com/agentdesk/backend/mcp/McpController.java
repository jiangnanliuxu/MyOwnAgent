package com.agentdesk.backend.mcp;

import com.agentdesk.backend.common.api.ApiResponse;
import com.agentdesk.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class McpController {

    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @GetMapping("/api/v1/projects/{projectId}/mcp")
    public ApiResponse<McpDtos.EndpointListResponse> listEndpoints(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.success(mcpService.listEndpoints(user, projectId));
    }

    @PostMapping("/api/v1/projects/{projectId}/mcp")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<McpDtos.EndpointResponse> createEndpoint(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody McpDtos.CreateEndpointRequest request
    ) {
        return ApiResponse.success(mcpService.createEndpoint(user, projectId, request));
    }

    @GetMapping("/api/v1/projects/{projectId}/mcp/tools")
    public ApiResponse<McpDtos.ToolRegistryResponse> toolRegistry(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.success(mcpService.toolRegistry(user, projectId));
    }

    @PostMapping("/api/v1/projects/{projectId}/mcp/health-check-all")
    public ApiResponse<McpDtos.HealthCheckAllResponse> checkAllByProject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.success(mcpService.checkAll(user, projectId));
    }

    @GetMapping("/api/v1/projects/{projectId}/mcp/health-status")
    public ApiResponse<McpDtos.HealthStatusResponse> healthStatusByProject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId
    ) {
        return ApiResponse.success(mcpService.healthStatus(user, projectId));
    }

    @GetMapping("/api/v1/mcp/{endpointId}")
    public ApiResponse<McpDtos.EndpointResponse> getEndpoint(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID endpointId
    ) {
        return ApiResponse.success(mcpService.getEndpoint(user, endpointId));
    }

    @PatchMapping("/api/v1/mcp/{endpointId}")
    public ApiResponse<McpDtos.EndpointResponse> patchEndpoint(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID endpointId,
            @Valid @RequestBody McpDtos.PatchEndpointRequest request
    ) {
        return ApiResponse.success(mcpService.patchEndpoint(user, endpointId, request));
    }

    @PostMapping("/api/v1/mcp/{endpointId}/health-check")
    public ApiResponse<McpDtos.HealthCheckResponse> checkEndpoint(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID endpointId,
            @RequestBody(required = false) McpDtos.HealthCheckRequest request
    ) {
        return ApiResponse.success(mcpService.checkEndpoint(user, endpointId, request));
    }

    @PostMapping("/internal/tools/invoke")
    public ApiResponse<McpDtos.ToolInvokeResponse> invokeTool(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody McpDtos.ToolInvokeRequest request
    ) {
        return ApiResponse.success(mcpService.invokeTool(token, request));
    }
}
