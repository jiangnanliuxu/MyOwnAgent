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
});
