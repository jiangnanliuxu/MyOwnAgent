import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useSkillStore } from '../src/stores/skill';

describe('skill store', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('toggles and prepends skills', () => {
    const store = useSkillStore();
    const skill = store.toggleSkill('frontend-design');
    expect(skill.status).toBe('停用');

    store.addSkill({ id: 'test-skill', name: 'test-skill', source: 'local skill', status: '启用', scope: '测试范围', mounts: ['任务层'], lastRun: '新建' });
    expect(store.skills[0].name).toBe('test-skill');
  });

  it('hydrates backend skills into the existing page shape', () => {
    const store = useSkillStore();
    const count = store.applyBackendSkills({
      items: [
        {
          id: '00000000-0000-0000-0000-000000000201',
          client_key: 'rag-reader',
          name: 'rag-reader',
          source: 'python skill',
          status: '启用',
          scope: 'RAG 检索增强',
          mounts: ['任务层'],
          last_run: '刚刚',
          manifest: {},
          config: {}
        }
      ]
    });

    expect(count).toBe(1);
    expect(store.skills).toHaveLength(1);
    expect(store.getSkill('rag-reader').backendId).toBe('00000000-0000-0000-0000-000000000201');
    expect(store.skills[0].lastRun).toBe('刚刚');
  });
});
