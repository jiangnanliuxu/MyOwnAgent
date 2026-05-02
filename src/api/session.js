const API_BASE_URL = import.meta.env.VITE_AGENT_DESK_API_BASE_URL || '';
const TOKEN_KEY = 'agentDesk.api.accessToken';
const PROJECT_KEY = 'agentDesk.api.projectId';

function storage() {
  return typeof window === 'undefined' ? null : window.localStorage;
}

export function apiBaseUrl() {
  return API_BASE_URL.replace(/\/+$/, '');
}

export function apiSession() {
  const localStorage = storage();
  return {
    baseUrl: apiBaseUrl(),
    accessToken: localStorage?.getItem(TOKEN_KEY) || '',
    projectId: localStorage?.getItem(PROJECT_KEY) || ''
  };
}

export function setApiSession({ accessToken, projectId }) {
  const localStorage = storage();
  if (!localStorage) return;
  if (accessToken) localStorage.setItem(TOKEN_KEY, accessToken);
  if (projectId) localStorage.setItem(PROJECT_KEY, projectId);
}

export function isBackendConfigured() {
  const session = apiSession();
  return Boolean(session.baseUrl && session.accessToken && session.projectId);
}
