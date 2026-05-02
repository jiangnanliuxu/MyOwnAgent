import { apiRequest } from './http';

export function listProjectRoles(projectId, { includeThreadRoles = true } = {}) {
  const query = includeThreadRoles ? '?include_thread_roles=true' : '';
  return apiRequest(`/api/v1/projects/${projectId}/roles${query}`);
}

export function getProjectRole(roleId) {
  return apiRequest(`/api/v1/roles/${roleId}`);
}

export function patchProjectRole(roleId, payload) {
  return apiRequest(`/api/v1/roles/${roleId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export function testRoleConnection(roleId, payload) {
  return apiRequest(`/api/v1/roles/${roleId}/test-connection`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function syncFolderRoles(folderId) {
  return apiRequest(`/api/v1/folders/${folderId}/sync-roles`, {
    method: 'POST'
  });
}
