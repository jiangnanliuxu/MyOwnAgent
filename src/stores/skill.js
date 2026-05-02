import { reactive } from 'vue';
import { defineStore } from 'pinia';
import { SKILL_CATALOG } from '../data';
import { apiSession, isBackendConfigured } from '../api/session';
import { createProjectSkill, listProjectSkills, patchProjectSkill, syncProjectSkillPolicy, toggleProjectSkill } from '../api/skills';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function mapBackendSkill(skill) {
  const clientKey = skill.client_key || skill.clientKey || skill.id;
  return {
    id: clientKey,
    backendId: skill.id,
    clientKey,
    name: skill.name,
    source: skill.source,
    status: skill.status,
    scope: skill.scope,
    mounts: Array.isArray(skill.mounts) ? skill.mounts : [],
    lastRunAt: skill.last_run_at || skill.lastRunAt,
    lastRun: skill.last_run || skill.lastRun || '未运行',
    manifest: skill.manifest || {},
    config: skill.config || {},
    createdAt: skill.created_at || skill.createdAt
  };
}

function skillToRequest(skill) {
  return {
    name: skill.name,
    client_key: skill.clientKey || skill.id,
    source: skill.source,
    status: skill.status,
    scope: skill.scope,
    mounts: skill.mounts || [],
    manifest: skill.manifest || {},
    config: skill.config || {}
  };
}

export const useSkillStore = defineStore('skill', () => {
  const skills = reactive(clone(SKILL_CATALOG));
  const backendState = reactive({ ready: false, error: '' });

  function getSkill(id) {
    return skills.find((skill) => skill.id === id);
  }

  function toggleSkill(id) {
    const skill = getSkill(id);
    if (!skill) return null;
    skill.status = skill.status === '启用' ? '停用' : '启用';
    if (isBackendConfigured() && skill.backendId) {
      toggleProjectSkill(skill.backendId, skill.status)
        .then((response) => Object.assign(skill, mapBackendSkill(response?.skill || response)))
        .catch((error) => {
          backendState.error = error.message || 'Skill 切换失败';
        });
    }
    return skill;
  }

  function saveSkill(skill, patch) {
    Object.assign(skill, patch);
    if (isBackendConfigured() && skill.backendId) {
      patchProjectSkill(skill.backendId, skillToRequest(skill))
        .then((response) => Object.assign(skill, mapBackendSkill(response?.skill || response)))
        .catch((error) => {
          backendState.error = error.message || 'Skill 保存失败';
        });
    }
    return skill;
  }

  function addSkill(skill) {
    const localSkill = { ...skill, clientKey: skill.clientKey || skill.id };
    skills.unshift(localSkill);
    if (isBackendConfigured()) {
      createProjectSkill(apiSession().projectId, skillToRequest(localSkill))
        .then((response) => {
          const created = mapBackendSkill(response?.skill || response);
          const index = skills.indexOf(localSkill);
          if (index >= 0) skills.splice(index, 1, created);
        })
        .catch((error) => {
          backendState.error = error.message || 'Skill 新增失败';
        });
    }
    return localSkill;
  }

  function applyBackendSkills(data) {
    const items = Array.isArray(data) ? data : data?.items || [];
    skills.splice(0, skills.length, ...items.map(mapBackendSkill));
    backendState.ready = Boolean(items.length);
    return items.length;
  }

  async function hydrateFromBackend() {
    if (!isBackendConfigured()) return false;
    try {
      applyBackendSkills(await listProjectSkills(apiSession().projectId));
      backendState.error = '';
      return true;
    } catch (error) {
      backendState.ready = false;
      backendState.error = error.message || 'Skill 加载失败';
      return false;
    }
  }

  async function syncPolicy() {
    if (!isBackendConfigured()) return null;
    try {
      const response = await syncProjectSkillPolicy(apiSession().projectId);
      if (response?.items) applyBackendSkills(response.items);
      backendState.error = '';
      return response;
    } catch (error) {
      backendState.error = error.message || 'Skill 策略同步失败';
      return null;
    }
  }

  return { skills, backendState, getSkill, toggleSkill, saveSkill, addSkill, applyBackendSkills, hydrateFromBackend, syncPolicy };
});
