const API_BASE_URL = import.meta.env.VITE_AGENT_DESK_API_BASE_URL || '';
export const API_BASE_URL_KEY = 'agentDesk.api.baseUrl';
export const TOKEN_KEY = 'agentDesk.api.accessToken';
export const REFRESH_TOKEN_KEY = 'agentDesk.api.refreshToken';
export const PROJECT_KEY = 'agentDesk.api.projectId';
export const USER_EMAIL_KEY = 'agentDesk.api.userEmail';
export const USER_NAME_KEY = 'agentDesk.api.userName';

function storage() {
  return typeof window === 'undefined' ? null : window.localStorage;
}

export function apiBaseUrl() {
  const configured = storage()?.getItem(API_BASE_URL_KEY) || API_BASE_URL;
  return configured.replace(/\/+$/, '');
}

export function apiSession() {
  const localStorage = storage();
  return {
    baseUrl: apiBaseUrl(),
    accessToken: localStorage?.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage?.getItem(REFRESH_TOKEN_KEY) || '',
    projectId: localStorage?.getItem(PROJECT_KEY) || '',
    userEmail: localStorage?.getItem(USER_EMAIL_KEY) || '',
    userName: localStorage?.getItem(USER_NAME_KEY) || ''
  };
}

export function setApiSession({ baseUrl, accessToken, refreshToken, projectId, userEmail, userName }) {
  const localStorage = storage();
  if (!localStorage) return;
  if (baseUrl) localStorage.setItem(API_BASE_URL_KEY, baseUrl.replace(/\/+$/, ''));
  if (accessToken) localStorage.setItem(TOKEN_KEY, accessToken);
  if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  if (projectId) localStorage.setItem(PROJECT_KEY, projectId);
  if (userEmail) localStorage.setItem(USER_EMAIL_KEY, userEmail);
  if (userName) localStorage.setItem(USER_NAME_KEY, userName);
}

export function clearApiSession({ keepBaseUrl = true } = {}) {
  const localStorage = storage();
  if (!localStorage) return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(PROJECT_KEY);
  localStorage.removeItem(USER_EMAIL_KEY);
  localStorage.removeItem(USER_NAME_KEY);
  if (!keepBaseUrl) localStorage.removeItem(API_BASE_URL_KEY);
}

export function isBackendUrlConfigured() {
  return Boolean(apiBaseUrl());
}

export function isBackendConfigured() {
  const session = apiSession();
  return Boolean(session.baseUrl && session.accessToken && session.projectId);
}
