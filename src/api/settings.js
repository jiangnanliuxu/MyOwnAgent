import { apiRequest } from './http';

export function fetchSettingsOverview(projectId) {
  return apiRequest(`/api/v1/projects/${projectId}/settings/overview`);
}

export function patchProjectSettings(projectId, payload) {
  return apiRequest(`/api/v1/projects/${projectId}/settings`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}
