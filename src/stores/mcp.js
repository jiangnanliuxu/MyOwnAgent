import { reactive } from 'vue';
import { defineStore } from 'pinia';
import { HEALTH_ITEMS, MCP_ENDPOINTS } from '../data';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

export const useMcpStore = defineStore('mcp', () => {
  const endpoints = reactive(clone(MCP_ENDPOINTS));
  const healthItems = reactive(clone(HEALTH_ITEMS));

  function getEndpoint(id) {
    return endpoints.find((endpoint) => endpoint.id === id);
  }

  function checkEndpoint(id) {
    const endpoint = getEndpoint(id);
    if (!endpoint) return null;
    endpoint.status = '已连接';
    endpoint.latency = `${Math.floor(30 + Math.random() * 120)}ms`;
    return endpoint;
  }

  function checkAll() {
    endpoints.forEach((endpoint, index) => {
      endpoint.status = '已连接';
      endpoint.latency = `${42 + index * 31}ms`;
    });
  }

  function addEndpoint(endpoint) {
    endpoints.unshift(endpoint);
    return endpoint;
  }

  return { endpoints, healthItems, getEndpoint, checkEndpoint, checkAll, addEndpoint };
});
