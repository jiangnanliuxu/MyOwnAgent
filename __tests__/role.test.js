import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useRoleStore } from '../src/stores/role';

describe('role store', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('switches and updates the active role', () => {
    const store = useRoleStore();
    store.setActiveRole('review');
    store.updateRole('review', { provider: 'Custom Provider' });
    expect(store.activeRole.name).toBe('review-agent');
    expect(store.activeRole.provider).toBe('Custom Provider');
  });

  it('hydrates backend roles by client key and resolves backend ids', () => {
    const store = useRoleStore();
    const count = store.applyBackendRoles({
      items: [
        {
          id: '00000000-0000-0000-0000-000000000101',
          client_key: 'thread-role-backend-review',
          name: 'backend-review-agent',
          alias: 'backend-review',
          tag: 'Session Role',
          description: '后端会话角色',
          short_description: '后端角色摘要',
          status: 'active',
          is_builtin: false,
          config: {
            model: 'GPT-5.4-mini',
            provider: 'OpenAI',
            secret_ref: 'secret://project/demo/roles/backend-review/api-key',
            prompt_prefix: '只看后端改动',
            tools: ['读代码'],
            prompt_layers: ['任务层']
          },
          bound_threads: []
        }
      ]
    });

    expect(count).toBe(1);
    expect(store.getRole('thread-role-backend-review').backendId).toBe('00000000-0000-0000-0000-000000000101');
    store.setActiveRole('00000000-0000-0000-0000-000000000101');
    expect(store.activeRole.name).toBe('backend-review-agent');
    expect(store.activeRole.apiKey).toBe('已托管 secret_ref');
  });
});
