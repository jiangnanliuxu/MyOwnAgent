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
});
