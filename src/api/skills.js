import { apiRequest } from './http';

export function listProjectSkills(projectId) {
  return apiRequest(`/api/v1/projects/${projectId}/skills`);
}

export function createProjectSkill(projectId, payload) {
  return apiRequest(`/api/v1/projects/${projectId}/skills`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function getProjectSkill(skillId) {
  return apiRequest(`/api/v1/skills/${skillId}`);
}

export function patchProjectSkill(skillId, payload) {
  return apiRequest(`/api/v1/skills/${skillId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function toggleProjectSkill(skillId, nextStatus) {
  return apiRequest(`/api/v1/skills/${skillId}/toggle`, {
    method: 'POST',
    body: JSON.stringify({ next_status: nextStatus })
  });
}

export function syncProjectSkillPolicy(projectId) {
  return apiRequest(`/api/v1/projects/${projectId}/skills/sync-policy`, {
    method: 'POST'
  });
}
