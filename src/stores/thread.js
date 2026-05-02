import { computed, reactive, ref } from 'vue';
import { defineStore } from 'pinia';
import { ACTIVE_THREAD_STORAGE_KEY } from '../data';
import { fetchBootstrap } from '../api/bootstrap';
import { uploadThreadRagFile } from '../api/rag';
import { isBackendConfigured, apiSession } from '../api/session';
import { syncFolderRoles } from '../api/roles';
import { createFolderThread, sendThreadMessage } from '../api/threads';
import { fetchThreadEvents } from '../services/sseClient';

const EMPTY_THREAD_ID = 'empty-workspace';
const THREAD_STATE_STORAGE_KEY = 'agentDesk.threadState.v1';
const DEFAULT_LLM_TIMEOUT_MS = 120000;
const MOCK_THREAD_KEYS = new Set([
  'session-review',
  'session-auth',
  'route-primary',
  'route-review',
  'route-test',
  'login-test',
  'login-snapshot'
]);
const EMPTY_CONTEXT = {
  id: EMPTY_THREAD_ID,
  label: '新会话',
  file: '未选择目录',
  folder: '未选择目录',
  summary: '请选择目录或新建会话',
  roles: ['primary'],
  focusRole: 'primary',
  roleStatus: '未编排',
  sessionRoleId: `thread-role-${EMPTY_THREAD_ID}`
};

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function getThreadRoleId(threadId) {
  return `thread-role-${threadId}`;
}

function stripTrailingSlash(value) {
  return String(value || '').replace(/\/+$|\\+$/g, '');
}

function hasFileExtension(value) {
  const lastSegment = stripTrailingSlash(value).split(/[\\/]/).pop() || '';
  return /\.[^./\\]+$/.test(lastSegment);
}

function getDirectoryFromPath(path) {
  const normalized = stripTrailingSlash(path);
  if (!normalized) return '未命名目录';
  if (!hasFileExtension(normalized)) return normalized;
  const parts = normalized.split(/[\\/]/).filter(Boolean);
  parts.pop();
  return parts.join('/') || normalized.replace(/\.[^/.]+$/, '') || '未命名目录';
}

function getContextFolder(context) {
  return context.folder || getDirectoryFromPath(context.file);
}

function readStoredThreadState() {
  if (typeof window === 'undefined') return {};
  try {
    return JSON.parse(window.localStorage.getItem(THREAD_STATE_STORAGE_KEY) || '{}');
  } catch {
    return {};
  }
}

function createConversationForContext(context) {
  return [];
}

function mapBackendMessages(messages = []) {
  return messages.map((message) => ({
    kind: message.role === 'user' ? 'user' : 'agent',
    title: message.agent_name || message.agentName || (message.role === 'user' ? '你' : '主助手'),
    text: message.content
  }));
}

function backendValue(source, snakeKey, camelKey, fallback = undefined) {
  return source?.[snakeKey] ?? source?.[camelKey] ?? fallback;
}

function isMockThreadKey(threadId) {
  return MOCK_THREAD_KEYS.has(threadId);
}

function sanitizeStoredState(state = {}) {
  const contexts = Object.fromEntries(
    Object.entries(state.contexts || {}).filter(([threadId]) => !isMockThreadKey(threadId))
  );
  const conversations = Object.fromEntries(
    Object.entries(state.conversations || {}).filter(([threadId]) => !isMockThreadKey(threadId))
  );
  const usedFolders = new Set(Object.values(contexts).map((context) => getContextFolder(context)));
  const folderOrder = (state.folderOrder || state.fileOrder || []).filter((folder) => usedFolders.has(getDirectoryFromPath(folder)));
  return { ...state, contexts, conversations, folderOrder, fileOrder: folderOrder };
}

export const useThreadStore = defineStore('thread', () => {
  const storedState = sanitizeStoredState(readStoredThreadState());
  const contexts = reactive({ ...(storedState.contexts || {}) });
  Object.values(contexts).forEach((context) => {
    context.folder = getContextFolder(context);
    context.sessionRoleId = context.sessionRoleId || getThreadRoleId(context.id);
    context.roleStatus = context.roleStatus || '未编排';
  });

  const storedFolderOrder = Array.isArray(storedState.folderOrder) ? storedState.folderOrder : [];
  const storedFileOrder = Array.isArray(storedState.fileOrder) ? storedState.fileOrder.map(getDirectoryFromPath) : [];
  const allContextFolders = [...new Set(Object.values(contexts).map((context) => getContextFolder(context)))];
  const folderOrder = ref([...new Set([...storedFolderOrder, ...storedFileOrder, ...allContextFolders])]);
  const currentThreadId = ref(EMPTY_THREAD_ID);
  const backendReady = ref(false);
  const backendError = ref('');
  const conversations = reactive({
    ...(storedState.conversations || {})
  });
  Object.values(contexts).forEach((context) => {
    conversations[context.id] = conversations[context.id] || createConversationForContext(context);
  });

  const currentContext = computed(() => getContext(currentThreadId.value));
  const currentConversation = computed(() => conversations[currentContext.value.id] || []);
  const fileGroups = computed(() =>
    folderOrder.value
      .map((folder) => ({
        file: folder,
        folder,
        threads: getThreadIdsForFolder(folder)
      }))
      .filter((group) => group.threads.length)
  );

  function persistState() {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(
      THREAD_STATE_STORAGE_KEY,
      JSON.stringify({
        contexts: clone(contexts),
        fileOrder: folderOrder.value,
        folderOrder: folderOrder.value,
        conversations: clone(conversations)
      })
    );
  }

  function isValidThread(threadId) {
    return Boolean(threadId && !isMockThreadKey(threadId) && contexts[threadId]);
  }

  function getContext(threadId) {
    return contexts[threadId] || firstContext() || EMPTY_CONTEXT;
  }

  function firstContext() {
    return Object.values(contexts).find((context) => !isMockThreadKey(context.id)) || null;
  }

  function getThreadIdsForFolder(folder) {
    const normalizedFolder = getDirectoryFromPath(folder);
    return Object.values(contexts)
      .filter((context) => getContextFolder(context) === normalizedFolder)
      .map((context) => context.id);
  }

  function getThreadIdsForFile(file) {
    return getThreadIdsForFolder(file);
  }

  function getPersistedThreadId(queryThreadId = null) {
    if (isValidThread(queryThreadId)) return queryThreadId;
    if (typeof window === 'undefined') return null;
    const stored = window.localStorage.getItem(ACTIVE_THREAD_STORAGE_KEY);
    return isValidThread(stored) ? stored : null;
  }

  function resolveThreadId(queryThreadId = null) {
    return getPersistedThreadId(queryThreadId) || firstContext()?.id || EMPTY_THREAD_ID;
  }

  function setActiveThread(threadId, persist = true) {
    const context = getContext(threadId);
    currentThreadId.value = context.id;
    if (persist && typeof window !== 'undefined') {
      window.localStorage.setItem(ACTIVE_THREAD_STORAGE_KEY, context.id);
    }
    return context;
  }

  function hydrateFromRoute(queryThreadId = null) {
    return setActiveThread(resolveThreadId(queryThreadId), true);
  }

  function normalizeFolderName(selection) {
    if (typeof selection === 'string') return getDirectoryFromPath(selection);
    const files = Array.isArray(selection) ? selection : Array.from(selection || []);
    const file = files[0] || selection;
    const relativePath = file?.webkitRelativePath || file?.name || '';
    if (!relativePath) return '未命名目录';
    const normalized = stripTrailingSlash(relativePath);
    const parts = normalized.split(/[\\/]/).filter(Boolean);
    if (file?.webkitRelativePath && parts.length > 1) return parts[0];
    return getDirectoryFromPath(normalized);
  }

  function createThreadId(prefix = 'thread') {
    const suffix = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
    return `${prefix}-${suffix}`;
  }

  function createThreadContext({ folder, file, label, summary, roles = ['primary', 'review'], focusRole = 'primary', roleStatus = '未编排' }) {
    const id = createThreadId(label.toLowerCase().replace(/[^a-z0-9]+/g, '-') || 'thread');
    const contextFolder = folder || getDirectoryFromPath(file);
    const context = {
      id,
      label,
      file: file || contextFolder,
      folder: contextFolder,
      summary,
      roles,
      focusRole,
      roleStatus,
      sessionRoleId: getThreadRoleId(id)
    };
    contexts[id] = context;
    conversations[id] = createConversationForContext(context);
    persistState();
    return context;
  }

  function applyBackendBootstrap(data) {
    if (!data?.threads) return false;
    Object.entries(data.threads).forEach(([threadKey, thread]) => {
      const clientKey = backendValue(thread, 'client_key', 'clientKey', threadKey);
      if (isMockThreadKey(clientKey)) return;
      const context = applyBackendThread(thread, data.recent_messages?.[threadKey] || [], threadKey);
      if (!conversations[context.id].length && !context.backendId) conversations[context.id] = createConversationForContext(context);
    });
    const folders = (data.folders || []).map((folder) => folder.name || folder.path).filter(Boolean);
    if (folders.length) {
      const foldersWithThreads = folders.filter((folder) => getThreadIdsForFolder(folder).length);
      folderOrder.value = [...new Set([...foldersWithThreads, ...folderOrder.value])];
    }
    backendReady.value = true;
    persistState();
    return true;
  }

  function applyBackendThread(thread, messages = [], fallbackKey = null) {
    const clientKey = backendValue(thread, 'client_key', 'clientKey', fallbackKey || thread.id);
    const folder = thread.folder || getDirectoryFromPath(thread.file);
    const context = {
      id: clientKey,
      backendId: thread.id,
      folderId: backendValue(thread, 'folder_id', 'folderId'),
      label: thread.label,
      file: thread.file || folder,
      folder,
      summary: thread.summary,
      roles: backendValue(thread, 'role_keys', 'roleKeys', thread.roles || ['primary']),
      focusRole: backendValue(thread, 'focus_role_key', 'focusRoleKey', 'primary'),
      roleStatus: backendValue(thread, 'role_status', 'roleStatus', '已编排'),
      sessionRoleId:
        backendValue(thread, 'session_role_key', 'sessionRoleKey')
        || backendValue(thread, 'session_role_id', 'sessionRoleId')
        || getThreadRoleId(clientKey)
    };
    contexts[context.id] = context;
    conversations[context.id] = mapBackendMessages(messages);
    persistState();
    return context;
  }

  async function hydrateFromBackend() {
    if (!isBackendConfigured()) return false;
    try {
      const { projectId } = apiSession();
      const data = await fetchBootstrap(projectId);
      backendError.value = '';
      return applyBackendBootstrap(data);
    } catch (error) {
      backendReady.value = false;
      backendError.value = error.message || '后端初始化失败';
      return false;
    }
  }

  async function sendMessageToBackend(threadId, content, options = {}) {
    const context = getContext(threadId);
    if (!isBackendConfigured() || !context.backendId) return null;
    const clientMessageId = options.clientMessageId || `web-${Date.now().toString(36)}`;
    const timeoutMs = options.timeoutMs || DEFAULT_LLM_TIMEOUT_MS;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(new Error('LLM 响应超时')), timeoutMs);
    try {
      const response = await sendThreadMessage(
        context.backendId,
        {
          client_message_id: clientMessageId,
          content,
          context: options.context || {},
          rag: options.rag || { enabled: true, scope: 'thread' }
        },
        clientMessageId,
        { signal: controller.signal }
      );
      const placeholderId = response?.agent_placeholder?.id || response?.agentPlaceholder?.id;
      const events = await fetchThreadEvents(context.backendId, 0, { replayOnly: true, signal: controller.signal });
      const applied = applyBackendAgentEvents(context.id, events, placeholderId);
      if (!applied) {
        throw new Error('后端没有返回 LLM 响应内容。');
      }
      backendError.value = '';
      return response;
    } catch (error) {
      const timedOut = error.name === 'AbortError' || error.message === 'LLM 响应超时';
      backendError.value = timedOut ? '超过 2 分钟未收到后端 LLM 响应。' : (error.message || 'SSE 回放失败');
      throw new Error(backendError.value);
    } finally {
      clearTimeout(timeoutId);
    }
  }

  async function uploadRagFile(threadId, file) {
    const context = getContext(threadId);
    if (!isBackendConfigured() || !context.backendId || !file) return null;
    return uploadThreadRagFile(context.backendId, file, {
      scope: 'thread',
      sourcePath: file.webkitRelativePath || file.name
    });
  }

  function addRelatedFolder(selection) {
    const folder = normalizeFolderName(selection);
    if (!folderOrder.value.includes(folder)) {
      folderOrder.value = [folder, ...folderOrder.value];
    }

    const context = createThreadContext({
      folder,
      file: folder,
      label: '会话 1',
      summary: '新的独立会话，等待输入任务',
      roles: ['primary', 'review', 'test'],
      focusRole: 'primary',
      roleStatus: '未编排'
    });
    conversations[context.id] = [];
    persistState();
    return setActiveThread(context.id, true);
  }

  function addRelatedFile(file) {
    return addRelatedFolder(file);
  }

  function addLocalThreadForFolder(folder) {
    const normalizedFolder = getDirectoryFromPath(folder);
    const existingCount = Object.values(contexts).filter((context) => getContextFolder(context) === normalizedFolder).length;
    const context = createThreadContext({
      folder: normalizedFolder,
      file: normalizedFolder,
      label: `会话 ${existingCount + 1}`,
      summary: '新的独立会话，等待输入任务',
      roles: ['primary', 'review', 'test'],
      focusRole: 'primary',
      roleStatus: '未编排'
    });
    conversations[context.id] = [];
    persistState();
    return setActiveThread(context.id, true);
  }

  async function addThreadForFolder(folder) {
    const normalizedFolder = getDirectoryFromPath(folder);
    const existingCount = Object.values(contexts).filter((context) => getContextFolder(context) === normalizedFolder).length;
    const label = `会话 ${existingCount + 1}`;
    const summary = '新的独立会话，等待输入任务';
    const folderContext = Object.values(contexts).find(
      (context) => getContextFolder(context) === normalizedFolder && context.folderId
    );

    if (!isBackendConfigured()) {
      return addLocalThreadForFolder(normalizedFolder);
    }
    if (!folderContext?.folderId) {
      throw new Error('当前目录还没有后端 folderId，请先刷新后端初始化数据。');
    }

    const response = await createFolderThread(folderContext.folderId, {
      label,
      summary,
      role_keys: ['primary', 'review', 'test'],
      focus_role_key: 'primary'
    });
    await syncFolderRoles(folderContext.folderId);
    backendError.value = '';
    const context = applyBackendThread(response.thread, response.initial_messages || response.initialMessages || []);
    return setActiveThread(context.id, true);
  }

  function addThreadForFile(file) {
    return addThreadForFolder(file);
  }

  function updateThread(threadId, patch) {
    const context = contexts[threadId];
    if (!context) return null;
    Object.assign(context, patch);
    persistState();
    return context;
  }

  function appendConversationBubble(threadId, bubble) {
    const context = getContext(threadId);
    if (!conversations[context.id]) {
      conversations[context.id] = createConversationForContext(context);
    }
    conversations[context.id].push(bubble);
    persistState();
  }

  function updateLastAgentBubble(threadId, patch) {
    const context = getContext(threadId);
    const conversation = conversations[context.id] || [];
    for (let index = conversation.length - 1; index >= 0; index -= 1) {
      if (conversation[index]?.kind === 'agent') {
        Object.assign(conversation[index], patch);
        persistState();
        return conversation[index];
      }
    }
    appendConversationBubble(context.id, { kind: 'agent', title: patch.title || '主助手', text: patch.text || '' });
    return conversations[context.id].at(-1);
  }

  function applyBackendAgentEvents(threadId, events, placeholderId) {
    if (!placeholderId) return false;
    let applied = false;
    events.forEach((event) => {
      const payload = event.payload?.data || event.payload;
      if (!payload) return;
      if (event.type === 'message_delta' && payload.message_id === placeholderId && payload.content) {
        updateLastAgentBubble(threadId, {
          title: payload.agent_name || '主助手',
          text: payload.content || ''
        });
        applied = true;
      }
      if (event.type === 'message_completed' && payload.id === placeholderId && payload.content) {
        updateLastAgentBubble(threadId, {
          title: payload.agent_name || '主助手',
          text: payload.content || ''
        });
        applied = true;
      }
    });
    return applied;
  }

  return {
    contexts,
    fileGroups,
    currentThreadId,
    backendReady,
    backendError,
    currentContext,
    currentConversation,
    conversations,
    getDirectoryFromPath,
    isValidThread,
    getContext,
    getThreadIdsForFolder,
    getThreadIdsForFile,
    getThreadRoleId,
    getPersistedThreadId,
    resolveThreadId,
    setActiveThread,
    hydrateFromRoute,
    hydrateFromBackend,
    applyBackendBootstrap,
    addRelatedFolder,
    addRelatedFile,
    addThreadForFolder,
    addThreadForFile,
    updateThread,
    appendConversationBubble,
    updateLastAgentBubble,
    applyBackendAgentEvents,
    sendMessageToBackend,
    uploadRagFile
  };
});
