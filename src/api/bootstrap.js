import { apiRequest } from './http';

export function fetchBootstrap(projectId) {
  return apiRequest(`/api/v1/projects/${projectId}/bootstrap`);
}
