import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useMcpStore } from '../src/stores/mcp';

describe('mcp store', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('checks a single endpoint and all endpoints', () => {
    const store = useMcpStore();
    const endpoint = store.checkEndpoint('figma');
    expect(endpoint.status).toBe('已连接');

    store.checkAll();
    expect(store.endpoints.every((item) => item.status === '已连接')).toBe(true);
  });

  it('hydrates backend MCP endpoints and health items', () => {
    const store = useMcpStore();
    const count = store.applyBackendEndpoints({
      items: [
        {
          id: '00000000-0000-0000-0000-000000000301',
          client_key: 'python-files',
          name: 'Python Files MCP',
          transport: 'streamable-http',
          status: '待连接',
          auth_type: 'internal token',
          url: 'http://agent-python:8001/mcp',
          tools: ['read_file', 'list_dir'],
          latency: '未检查',
          health_config: {}
        }
      ]
    });
    const healthCount = store.applyBackendHealth({
      items: [['MCP 连接', '待确认', 'Python MCP 等待检查']]
    });

    expect(count).toBe(1);
    expect(healthCount).toBe(1);
    expect(store.endpoints[0].id).toBe('python-files');
    expect(store.endpoints[0].tools).toBe('read_file, list_dir');
    expect(store.healthItems[0][2]).toBe('Python MCP 等待检查');
  });
});
