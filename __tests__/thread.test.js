import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { ACTIVE_THREAD_STORAGE_KEY } from '../src/data';
import { useThreadStore } from '../src/stores/thread';

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

describe('thread store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    installLocalStorage();
  });

  it('uses valid query thread before storage', () => {
    const store = useThreadStore();
    window.localStorage.setItem(ACTIVE_THREAD_STORAGE_KEY, 'route-review');
    const context = store.hydrateFromRoute('login-snapshot');
    expect(context.id).toBe('login-snapshot');
    expect(store.currentContext.file).toBe('tests/auth-login.spec.ts');
    expect(store.currentContext.folder).toBe('tests');
  });

  it('groups threads by folder instead of exact file', () => {
    const store = useThreadStore();
    expect(store.getThreadIdsForFolder('src/auth')).toEqual(['session-review', 'session-auth']);
    expect(store.getThreadIdsForFile('src/auth/useSession.ts')).toEqual(['session-review', 'session-auth']);
  });

  it('falls back to default for unknown thread ids', () => {
    const store = useThreadStore();
    const context = store.hydrateFromRoute('missing-thread');
    expect(context.id).toBe('session-review');
  });

  it('can hydrate thread contexts from backend bootstrap payload', () => {
    const store = useThreadStore();
    const applied = store.applyBackendBootstrap({
      folders: [{ name: 'backend/src', path: 'backend/src' }],
      threads: {
        'backend-review': {
          id: '00000000-0000-0000-0000-000000000001',
          client_key: 'backend-review',
          folder: 'backend/src',
          file: 'backend/src/App.java',
          label: 'review-agent',
          summary: '后端审查',
          role_keys: ['primary', 'review'],
          focus_role_key: 'review',
          role_status: '已编排'
        }
      },
      recent_messages: {
        'backend-review': [{ role: 'agent', agent_name: 'review-agent', content: '后端消息', status: 'completed' }]
      }
    });

    expect(applied).toBe(true);
    expect(store.getContext('backend-review').backendId).toBe('00000000-0000-0000-0000-000000000001');
    expect(store.getThreadIdsForFolder('backend/src')).toEqual(['backend-review']);
    expect(store.conversations['backend-review'][0].text).toBe('后端消息');
  });
});
