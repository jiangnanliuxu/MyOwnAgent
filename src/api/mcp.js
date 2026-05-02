import { apiRequest } from './http';

export function listMcpEndpoints(projectId) {
  return apiRequest(`/api/v1/projects/${projectId}/mcp`);
}

export function createMcpEndpoint(projectId, payload) {
  return apiRequest(`/api/v1/projects/${projectId}/mcp`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function patchMcpEndpoint(endpointId, payload) {
  return apiRequest(`/api/v1/mcp/${endpointId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function checkMcpEndpoint(endpointId, timeoutMs = 3000) {
  return apiRequest(`/api/v1/mcp/${endpointId}/health-check`, {
    method: 'POST',
    body: JSON.stringify({ timeout_ms: timeoutMs })
  });
}

export function checkAllMcpEndpoints(projectId) {
  return apiRequest(`/api/v1/projects/${projectId}/mcp/health-check-all`, {
    method: 'POST'
  });
}

export function fetchMcpHealthStatus(projectId) {
  return apiRequest(`/api/v1/projects/${projectId}/mcp/health-status`);
}
