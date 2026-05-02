import { computed, reactive } from 'vue';
import { defineStore } from 'pinia';
import { fetchMe, login as loginRequest, register as registerRequest } from '../api/auth';
import { apiSession, clearApiSession, isBackendUrlConfigured, setApiSession } from '../api/session';

function readSessionState() {
  const session = apiSession();
  return {
    baseUrl: session.baseUrl,
    accessToken: session.accessToken,
    refreshToken: session.refreshToken,
    projectId: session.projectId,
    userEmail: session.userEmail,
    userName: session.userName
  };
}

function normalizeAuthResponse(data) {
  const user = data?.user || {};
  return {
    accessToken: data?.access_token || data?.accessToken || '',
    refreshToken: data?.refresh_token || data?.refreshToken || '',
    projectId: data?.default_project_id || data?.defaultProjectId || user.default_project_id || user.defaultProjectId || '',
    userEmail: user.email || '',
    userName: user.name || ''
  };
}

export const useBackendSessionStore = defineStore('backendSession', () => {
  const state = reactive({
    ...readSessionState(),
    loading: false,
    error: ''
  });

  const hasBaseUrl = computed(() => Boolean(state.baseUrl || isBackendUrlConfigured()));
  const isAuthenticated = computed(() => Boolean(state.baseUrl && state.accessToken && state.projectId));
  const displayName = computed(() => state.userName || state.userEmail || '后端账户');

  function refreshFromStorage() {
    Object.assign(state, readSessionState());
  }

  function setBaseUrl(baseUrl) {
    const normalized = String(baseUrl || '').trim().replace(/\/+$/, '');
    if (!normalized) {
      state.error = '请填写后端地址';
      return false;
    }
    setApiSession({ baseUrl: normalized });
    refreshFromStorage();
    state.error = '';
    return true;
  }

  function persistAuth(data) {
    const auth = normalizeAuthResponse(data);
    setApiSession(auth);
    refreshFromStorage();
    return auth;
  }

  async function login({ email, password }) {
    state.loading = true;
    state.error = '';
    try {
      const response = await loginRequest({ email, password });
      return persistAuth(response);
    } catch (error) {
      state.error = error.message || '登录失败';
      return null;
    } finally {
      state.loading = false;
    }
  }

  async function register({ email, name, password }) {
    state.loading = true;
    state.error = '';
    try {
      const response = await registerRequest({ email, name, password });
      return persistAuth(response);
    } catch (error) {
      state.error = error.message || '注册失败';
      return null;
    } finally {
      state.loading = false;
    }
  }

  async function loadMe() {
    if (!isAuthenticated.value) return null;
    try {
      const user = await fetchMe();
      setApiSession({
        projectId: user.default_project_id || user.defaultProjectId || state.projectId,
        userEmail: user.email,
        userName: user.name
      });
      refreshFromStorage();
      return user;
    } catch (error) {
      state.error = error.message || '账户状态检查失败';
      return null;
    }
  }

  function logout({ keepBaseUrl = true } = {}) {
    clearApiSession({ keepBaseUrl });
    refreshFromStorage();
    state.error = '';
  }

  return {
    state,
    hasBaseUrl,
    isAuthenticated,
    displayName,
    refreshFromStorage,
    setBaseUrl,
    login,
    register,
    loadMe,
    logout
  };
});
