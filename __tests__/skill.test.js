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
});
