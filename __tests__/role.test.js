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
});
