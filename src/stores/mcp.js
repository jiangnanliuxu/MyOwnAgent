import { reactive } from 'vue';
import { defineStore } from 'pinia';
import { HEALTH_ITEMS, MCP_ENDPOINTS } from '../data';
import { apiSession, isBackendConfigured } from '../api/session';
import { checkAllMcpEndpoints, checkMcpEndpoint, createMcpEndpoint, fetchMcpHealthStatus, listMcpEndpoints, patchMcpEndpoint } from '../api/mcp';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function asToolText(tools) {
  return Array.isArray(tools) ? tools.join(', ') : tools || '';
}

function parseToolText(tools) {
  return String(tools || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function mapBackendEndpoint(endpoint) {
  const clientKey = endpoint.client_key || endpoint.clientKey || endpoint.id;
  return {
    id: clientKey,
    backendId: endpoint.id,
    clientKey,
    name: endpoint.name,
    transport: endpoint.transport,
    status: endpoint.status,
    authType: endpoint.auth_type || endpoint.authType,
    auth: endpoint.auth || endpoint.auth_type || endpoint.authType || 'local',
    url: endpoint.url,
    command: endpoint.command || {},
    tools: asToolText(endpoint.tools),
    latencyMs: endpoint.latency_ms ?? endpoint.latencyMs,
    latency: endpoint.latency || (endpoint.latency_ms ? `${endpoint.latency_ms}ms` : '未检查'),
    secretRef: endpoint.secret_ref || endpoint.secretRef,
    healthConfig: endpoint.health_config || endpoint.healthConfig || {},
    createdAt: endpoint.created_at || endpoint.createdAt,
    updatedAt: endpoint.updated_at || endpoint.updatedAt
  };
}

function endpointToRequest(endpoint) {
  return {
    name: endpoint.name,
    client_key: endpoint.clientKey || endpoint.id,
    transport: endpoint.transport,
    status: endpoint.status,
    auth_type: endpoint.authType || endpoint.auth,
    url: endpoint.url,
    command: endpoint.command || {},
    tools: parseToolText(endpoint.tools),
    health_config: endpoint.healthConfig || {}
  };
}

export const useMcpStore = defineStore('mcp', () => {
  const endpoints = reactive(clone(MCP_ENDPOINTS));
  const healthItems = reactive(clone(HEALTH_ITEMS));
  const backendState = reactive({ ready: false, error: '' });

  function getEndpoint(id) {
    return endpoints.find((endpoint) => endpoint.id === id);
  }

  function checkEndpoint(id) {
    const endpoint = getEndpoint(id);
    if (!endpoint) return null;
    endpoint.status = '已连接';
    endpoint.latency = `${Math.floor(30 + Math.random() * 120)}ms`;
    if (isBackendConfigured() && endpoint.backendId) {
      checkMcpEndpoint(endpoint.backendId)
        .then((response) => {
          const result = response?.result || response;
          endpoint.status = result.status;
          endpoint.latency = result.latency;
          endpoint.latencyMs = result.latency_ms ?? result.latencyMs;
        })
        .catch((error) => {
          backendState.error = error.message || 'MCP 检查失败';
        });
    }
    return endpoint;
  }

  function checkAll() {
    endpoints.forEach((endpoint, index) => {
      endpoint.status = '已连接';
      endpoint.latency = `${42 + index * 31}ms`;
    });
    if (isBackendConfigured()) {
      checkAllMcpEndpoints(apiSession().projectId)
        .then((response) => {
          (response?.results || []).forEach((result) => {
            const endpoint = endpoints.find((item) => item.backendId === result.id || item.id === result.id);
            if (endpoint) {
              endpoint.status = result.status;
              endpoint.latency = result.latency;
              endpoint.latencyMs = result.latency_ms ?? result.latencyMs;
            }
          });
        })
        .catch((error) => {
          backendState.error = error.message || 'MCP 批量检查失败';
        });
    }
  }

  function addEndpoint(endpoint) {
    const localEndpoint = { ...endpoint, clientKey: endpoint.clientKey || endpoint.id };
    endpoints.unshift(localEndpoint);
    if (isBackendConfigured()) {
      createMcpEndpoint(apiSession().projectId, endpointToRequest(localEndpoint))
        .then((response) => {
          const created = mapBackendEndpoint(response?.endpoint || response);
          const index = endpoints.indexOf(localEndpoint);
          if (index >= 0) endpoints.splice(index, 1, created);
        })
        .catch((error) => {
          backendState.error = error.message || 'MCP 新增失败';
        });
    }
    return localEndpoint;
  }

  function saveEndpoint(endpoint, patch) {
    Object.assign(endpoint, patch);
    if (isBackendConfigured() && endpoint.backendId) {
      patchMcpEndpoint(endpoint.backendId, endpointToRequest(endpoint))
        .then((response) => Object.assign(endpoint, mapBackendEndpoint(response?.endpoint || response)))
        .catch((error) => {
          backendState.error = error.message || 'MCP 保存失败';
        });
    }
    return endpoint;
  }

  function applyBackendEndpoints(data) {
    const items = Array.isArray(data) ? data : data?.items || [];
    endpoints.splice(0, endpoints.length, ...items.map(mapBackendEndpoint));
    backendState.ready = Boolean(items.length);
    return items.length;
  }

  function applyBackendHealth(data) {
    const items = Array.isArray(data) ? data : data?.items || [];
    if (!items.length) return 0;
    healthItems.splice(0, healthItems.length, ...items);
    return items.length;
  }

  async function hydrateFromBackend() {
    if (!isBackendConfigured()) return false;
    try {
      const { projectId } = apiSession();
      applyBackendEndpoints(await listMcpEndpoints(projectId));
      applyBackendHealth(await fetchMcpHealthStatus(projectId));
      backendState.error = '';
      return true;
    } catch (error) {
      backendState.ready = false;
      backendState.error = error.message || 'MCP 加载失败';
      return false;
    }
  }

  return { endpoints, healthItems, backendState, getEndpoint, checkEndpoint, checkAll, addEndpoint, saveEndpoint, applyBackendEndpoints, applyBackendHealth, hydrateFromBackend };
});
