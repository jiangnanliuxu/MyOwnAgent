import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { apiSession, API_BASE_URL_KEY, PROJECT_KEY, TOKEN_KEY } from '../src/api/session';
import { useBackendSessionStore } from '../src/stores/backendSession';

function installLocalStorage() {
  const storage = new Map();
  globalThis.window = {
    localStorage: {
      getItem: (key) => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, String(value)),
      removeItem: (key) => storage.delete(key),
      clear: () => storage.clear()
    }
  };
}

function jsonResponse(data) {
  return {
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    json: async () => ({ code: '0', message: 'ok', data })
  };
}

describe('backend session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    installLocalStorage();
    globalThis.fetch = vi.fn();
  });

  it('saves backend URL and persists login session without storing passwords', async () => {
    const store = useBackendSessionStore();
    expect(store.isAuthenticated).toBe(false);

    store.setBaseUrl('http://localhost:18080/');
    expect(window.localStorage.getItem(API_BASE_URL_KEY)).toBe('http://localhost:18080');

    fetch.mockResolvedValueOnce(jsonResponse({
      access_token: 'access-token',
      refresh_token: 'refresh-token',
      default_project_id: '00000000-0000-0000-0000-000000000401',
      user: {
        email: 'dev@example.com',
        name: 'Dev User'
      }
    }));

    const result = await store.login({ email: 'dev@example.com', password: 'local-password' });
    expect(result.projectId).toBe('00000000-0000-0000-0000-000000000401');
    expect(window.localStorage.getItem(TOKEN_KEY)).toBe('access-token');
    expect(window.localStorage.getItem(PROJECT_KEY)).toBe('00000000-0000-0000-0000-000000000401');
    expect(JSON.stringify(apiSession())).not.toContain('local-password');
    expect(store.isAuthenticated).toBe(true);
  });

  it('clears auth data on logout and keeps backend URL by default', async () => {
    const store = useBackendSessionStore();
    store.setBaseUrl('http://localhost:18080');
    window.localStorage.setItem(TOKEN_KEY, 'access-token');
    window.localStorage.setItem(PROJECT_KEY, 'project-id');
    store.refreshFromStorage();

    store.logout();
    expect(store.state.baseUrl).toBe('http://localhost:18080');
    expect(store.state.accessToken).toBe('');
    expect(store.state.projectId).toBe('');
  });
});
