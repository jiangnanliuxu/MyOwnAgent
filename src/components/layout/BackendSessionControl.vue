<script setup>
import { onMounted } from 'vue';
import { useModal } from '../../composables/useModal';
import { useToast } from '../../composables/useToast';
import { useBackendSessionStore } from '../../stores/backendSession';

const sessionStore = useBackendSessionStore();
const { open } = useModal();
const { show: showToast } = useToast();

function escapeHTML(value) {
  return String(value || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function openBaseUrlModal() {
  open({
    modalTitle: '后端地址',
    modalSubtitle: '配置 Spring 后端地址后，才能登录或注册后端会话。',
    saveLabel: '保存地址',
    content: `
      <div class="modal-form-grid">
        <label class="modal-field modal-field-full">
          <span>后端 API 地址</span>
          <input class="text-input" id="backend-base-url-input" type="url" value="${escapeHTML(sessionStore.state.baseUrl || 'http://localhost:18080')}" />
        </label>
      </div>
    `,
    onSave(modalBody) {
      const saved = sessionStore.setBaseUrl(modalBody.querySelector('#backend-base-url-input').value);
      showToast(saved ? '后端地址已保存' : sessionStore.state.error);
    }
  });
}

function openAuthModal() {
  open({
    modalTitle: '连接后端',
    modalSubtitle: '登录或注册后，会保存访问 token 和默认项目 ID 到本机 localStorage。',
    saveLabel: '连接后端',
    content: `
      <div class="modal-form-grid">
        <label class="modal-field">
          <span>模式</span>
          <select class="text-input" id="backend-auth-mode-input">
            <option value="login">登录</option>
            <option value="register">注册</option>
          </select>
        </label>
        <label class="modal-field">
          <span>显示名称（注册时使用）</span>
          <input class="text-input" id="backend-name-input" type="text" value="Local Developer" />
        </label>
        <label class="modal-field modal-field-full">
          <span>邮箱</span>
          <input class="text-input" id="backend-email-input" type="email" autocomplete="username" />
        </label>
        <label class="modal-field modal-field-full">
          <span>密码</span>
          <input class="text-input" id="backend-password-input" type="password" autocomplete="current-password" />
        </label>
      </div>
    `,
    async onSave(modalBody) {
      const mode = modalBody.querySelector('#backend-auth-mode-input').value;
      const payload = {
        email: modalBody.querySelector('#backend-email-input').value.trim(),
        name: modalBody.querySelector('#backend-name-input').value.trim() || 'Local Developer',
        password: modalBody.querySelector('#backend-password-input').value
      };
      const result = mode === 'register'
        ? await sessionStore.register(payload)
        : await sessionStore.login({ email: payload.email, password: payload.password });
      showToast(result ? '后端会话已连接' : sessionStore.state.error);
    }
  });
}

function openConnectedModal() {
  open({
    modalTitle: '后端会话',
    modalSubtitle: '当前浏览器已保存后端访问凭据，可在这里退出。',
    saveLabel: '退出登录',
    content: `
      <div class="value-box modal-field-full">
        后端：${escapeHTML(sessionStore.state.baseUrl)}<br />
        账户：${escapeHTML(sessionStore.displayName)}<br />
        项目：${escapeHTML(sessionStore.state.projectId)}
      </div>
    `,
    onSave() {
      sessionStore.logout();
      showToast('已退出后端会话');
    }
  });
}

function handleClick() {
  if (!sessionStore.hasBaseUrl) {
    openBaseUrlModal();
    return;
  }
  if (!sessionStore.isAuthenticated) {
    openAuthModal();
    return;
  }
  openConnectedModal();
}

onMounted(() => {
  sessionStore.refreshFromStorage();
  sessionStore.loadMe();
});
</script>

<template>
  <button
    class="nav-pill backend-session-pill"
    :class="{ 'is-connected': sessionStore.isAuthenticated }"
    id="backend-session-control"
    type="button"
    @click="handleClick"
  >
    <span class="backend-session-dot" aria-hidden="true"></span>
    <span>{{ sessionStore.isAuthenticated ? '后端已连接' : sessionStore.hasBaseUrl ? '连接后端' : 'Mock 模式' }}</span>
  </button>
</template>
