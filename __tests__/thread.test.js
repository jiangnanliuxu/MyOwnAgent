import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { ACTIVE_THREAD_STORAGE_KEY } from '../src/data';
import { useThreadStore } from '../src/stores/thread';
import { parseEventStream } from '../src/services/sseClient';

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
    store.applyBackendBootstrap({
      folders: [{ name: 'backend/src', path: 'backend/src' }],
      threads: {
        'backend-review': {
          id: '00000000-0000-0000-0000-000000000001',
          client_key: 'backend-review',
          folder_id: '00000000-0000-0000-0000-000000000010',
          folder: 'backend/src',
          file: 'backend/src/App.java',
          label: 'review-agent',
          summary: '后端审查',
          role_keys: ['primary', 'review'],
          focus_role_key: 'review',
          role_status: '已编排'
        },
        'backend-test': {
          id: '00000000-0000-0000-0000-000000000002',
          client_key: 'backend-test',
          folder_id: '00000000-0000-0000-0000-000000000010',
          folder: 'backend/src',
          file: 'backend/src/Test.java',
          label: 'test-agent',
          summary: '后端测试',
          role_keys: ['primary', 'test'],
          focus_role_key: 'test',
          role_status: '已编排'
        }
      }
    });
    window.localStorage.setItem(ACTIVE_THREAD_STORAGE_KEY, 'backend-review');
    const context = store.hydrateFromRoute('backend-test');
    expect(context.id).toBe('backend-test');
    expect(store.currentContext.file).toBe('backend/src/Test.java');
    expect(store.currentContext.folder).toBe('backend/src');
  });

  it('groups threads by folder instead of exact file', () => {
    const store = useThreadStore();
    store.addRelatedFolder('src/auth/useSession.ts');
    const threadIds = store.getThreadIdsForFolder('src/auth');
    expect(threadIds).toHaveLength(1);
    expect(store.getThreadIdsForFile('src/auth/useSession.ts')).toEqual(threadIds);
  });

  it('falls back to default for unknown thread ids', () => {
    const store = useThreadStore();
    const context = store.hydrateFromRoute('missing-thread');
    expect(context.id).toBe('empty-workspace');
  });

  it('can hydrate thread contexts from backend bootstrap payload', () => {
    const store = useThreadStore();
    const applied = store.applyBackendBootstrap({
      folders: [{ name: 'backend/src', path: 'backend/src' }],
      threads: {
        'backend-review': {
          id: '00000000-0000-0000-0000-000000000001',
          client_key: 'backend-review',
          folder_id: '00000000-0000-0000-0000-000000000010',
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
    expect(store.getContext('backend-review').folderId).toBe('00000000-0000-0000-0000-000000000010');
    expect(store.getThreadIdsForFolder('backend/src')).toEqual(['backend-review']);
    expect(store.conversations['backend-review'][0].text).toBe('后端消息');
  });

  it('keeps backend-created threads empty when there are no messages', () => {
    const store = useThreadStore();
    store.applyBackendBootstrap({
      folders: [{ name: 'src/auth', path: 'src/auth' }],
      threads: {
        'thread-empty': {
          id: '00000000-0000-0000-0000-000000000011',
          client_key: 'thread-empty',
          folder_id: '00000000-0000-0000-0000-000000000010',
          folder: 'src/auth',
          file: 'src/auth',
          label: '会话 3',
          summary: '新的独立会话',
          role_keys: ['primary'],
          focus_role_key: 'primary',
          role_status: '未编排'
        }
      },
      recent_messages: {
        'thread-empty': []
      }
    });

    expect(store.conversations['thread-empty']).toEqual([]);
  });

  it('does not show backend seed mock threads in recent folders', () => {
    const store = useThreadStore();
    store.applyBackendBootstrap({
      folders: [{ name: 'src/auth', path: 'src/auth' }],
      threads: {
        'session-review': {
          id: '00000000-0000-0000-0000-000000000021',
          client_key: 'session-review',
          folder_id: '00000000-0000-0000-0000-000000000010',
          folder: 'src/auth',
          file: 'src/auth/useSession.ts',
          label: 'review-agent',
          summary: '模拟会话',
          role_keys: ['primary'],
          focus_role_key: 'primary',
          role_status: '已编排'
        }
      }
    });

    expect(store.getThreadIdsForFolder('src/auth')).toEqual([]);
    expect(store.fileGroups).toEqual([]);
  });

  it('parses backend SSE replay and applies the completed agent message', () => {
    const store = useThreadStore();
    const context = store.addRelatedFolder('src/auth');
    store.appendConversationBubble(context.id, { kind: 'agent', title: '主助手', text: '等待模型回应...' });
    const events = parseEventStream(`
id: 7
event: message_completed
data: {"id":7,"thread_id":"thread","type":"message_completed","data":{"id":"placeholder-1","agent_name":"主助手","content":"真实 LLM 回复"},"created_at":"2026-05-02T00:00:00Z"}

`);

    store.applyBackendAgentEvents(context.id, events, 'placeholder-1');

    expect(store.currentConversation.at(-1).text).toBe('真实 LLM 回复');
  });
});
