package com.agentdesk.backend.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class McpDtos {

    private McpDtos() {
    }

    public record EndpointListResponse(List<EndpointItem> items) {
    }

    public record EndpointResponse(EndpointItem endpoint) {
    }

    public record HealthCheckResponse(HealthResult result) {
    }

    public record HealthCheckAllResponse(List<HealthResult> results, HealthSummary summary) {
    }

    public record HealthStatusResponse(List<List<String>> items) {
    }

    public record ToolRegistryResponse(List<ToolItem> items) {
    }

    public record ToolInvokeResponse(
            UUID invocationId,
            String status,
            String endpointName,
            String toolName,
            JsonNode result,
            Instant auditedAt
    ) {
    }

    public record CreateEndpointRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 120) String clientKey,
            @NotBlank String transport,
            String status,
            @Size(max = 120) String authType,
            @Size(max = 1000) String url,
            JsonNode command,
            List<String> tools,
            JsonNode healthConfig,
            String secretInput
    ) {
    }

    public record PatchEndpointRequest(
            @Size(max = 160) String name,
            String transport,
            String status,
            @Size(max = 120) String authType,
            @Size(max = 1000) String url,
            JsonNode command,
            List<String> tools,
            JsonNode healthConfig,
            String secretInput
    ) {
    }

    public record HealthCheckRequest(Integer timeoutMs) {
    }

    public record ToolInvokeRequest(
            UUID projectId,
            UUID endpointId,
            String endpointClientKey,
            @NotBlank @Size(max = 160) String toolName,
            JsonNode arguments,
            Integer timeoutMs
    ) {
    }

    public record EndpointItem(
            UUID id,
            String clientKey,
            String name,
            String transport,
            String status,
            String authType,
            String auth,
            String url,
            JsonNode command,
            List<String> tools,
            Integer latencyMs,
            String latency,
            String secretRef,
            JsonNode healthConfig,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record HealthResult(
            UUID id,
            String name,
            String status,
            Integer latencyMs,
            String latency,
            List<String> toolsAvailable,
            String error,
            Instant checkedAt
    ) {
    }

    public record HealthSummary(int total, int connected, int failed, Instant checkedAt) {
    }

    public record ToolItem(
            UUID endpointId,
            String endpointClientKey,
            String endpointName,
            String transport,
            String status,
            String toolName
    ) {
    }
}
