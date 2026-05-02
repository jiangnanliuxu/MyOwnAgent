import { computed, reactive, ref } from 'vue';
import { defineStore } from 'pinia';
import { ACTIVE_THREAD_STORAGE_KEY, THREAD_CONTEXTS } from '../data';
import { fetchBootstrap } from '../api/bootstrap';
import { uploadThreadRagFile } from '../api/rag';
import { isBackendConfigured, apiSession } from '../api/session';
import { syncFolderRoles } from '../api/roles';
import { createFolderThread, sendThreadMessage } from '../api/threads';
import { fetchThreadEvents } from '../services/sseClient';

const DEFAULT_THREAD_ID = 'session-review';
const THREAD_STATE_STORAGE_KEY = 'agentDesk.threadState.v1';
const DEFAULT_LLM_TIMEOUT_MS = 120000;

const DEFAULT_CONVERSATION = [
  { kind: 'user', title: '你', text: '我想尽快知道这次重构会影响哪些页面和测试。' },
  { kind: 'agent', title: '主助手', text: '我已经锁定认证模块、路由守卫和两组回归测试。建议先跑关键路径，再把差异归类。' },
  { kind: 'user', title: '你', text: '那先告诉我最可能改动的文件，再给一个最小验证顺序。' },
  {
    kind: 'agent',
    id: 'suggestion-bubble',
    title: '主助手',
    text: '优先看 `src/auth`、`src/router` 和 `tests` 这三个目录，然后先验证登录主路径。'
  }
];

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
  const folder = getContextFolder(context);
  if (context.id === DEFAULT_THREAD_ID) {
    return clone(DEFAULT_CONVERSATION);
  }

  return [
    {
      kind: 'user',
      title: '你',
      text: `切到 ${folder}，先看 ${context.label} 这条会话。`
    },
    {
      kind: 'agent',
      title: '主助手',
      text: `${context.summary}。当前会话会优先调度 ${context.roles.join('、')} 这些角色，并保留独立的消息上下文。`
    },
    {
      kind: 'agent',
      id: `suggestion-${context.id}`,
      title: context.label,
      text: `这个会话绑定到 ${folder} 目录，切换到其他会话时这里会展示另一组对话内容。`
    }
  ];
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

export const useThreadStore = defineStore('thread', () => {
  const storedState = readStoredThreadState();
  const defaultContexts = clone(THREAD_CONTEXTS);
  const contexts = reactive({ ...defaultContexts, ...(storedState.contexts || {}) });
  Object.values(contexts).forEach((context) => {
    context.folder = getContextFolder(context);
    context.sessionRoleId = context.sessionRoleId || getThreadRoleId(context.id);
    context.roleStatus = context.roleStatus || (defaultContexts[context.id] ? '已编排' : '未编排');
  });

  const defaultFolderOrder = [...new Set(Object.values(defaultContexts).map((context) => getContextFolder(context)))];
  const storedFolderOrder = Array.isArray(storedState.folderOrder) ? storedState.folderOrder : [];
  const storedFileOrder = Array.isArray(storedState.fileOrder) ? storedState.fileOrder.map(getDirectoryFromPath) : [];
  const allContextFolders = [...new Set(Object.values(contexts).map((context) => getContextFolder(context)))];
  const folderOrder = ref([...new Set([...storedFolderOrder, ...storedFileOrder, ...defaultFolderOrder, ...allContextFolders])]);
  const currentThreadId = ref(DEFAULT_THREAD_ID);
  const backendReady = ref(false);
  const backendError = ref('');
  const conversations = reactive({
    ...Object.fromEntries(Object.values(defaultContexts).map((context) => [context.id, createConversationForContext(context)])),
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
    return Boolean(threadId && contexts[threadId]);
  }

  function getContext(threadId) {
    return contexts[threadId] || contexts[DEFAULT_THREAD_ID];
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
    return getPersistedThreadId(queryThreadId) || DEFAULT_THREAD_ID;
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
      const context = applyBackendThread(thread, data.recent_messages?.[threadKey] || [], threadKey);
      if (!conversations[context.id].length && !context.backendId) conversations[context.id] = createConversationForContext(context);
    });
    const folders = (data.folders || []).map((folder) => folder.name || folder.path).filter(Boolean);
    if (folders.length) {
      folderOrder.value = [...new Set([...folders, ...folderOrder.value])];
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
      label: 'primary-agent',
      summary: '新关联目录已加入，等待补充任务目标',
      roles: ['primary', 'review'],
      focusRole: 'primary',
      roleStatus: '未编排'
    });
    conversations[context.id] = [
      {
        kind: 'agent',
        title: '主助手',
        text: `已关联 ${folder} 目录。你可以直接在输入框里描述要分析、修改或验证的目标。`
      },
      {
        kind: 'agent',
        id: `suggestion-${context.id}`,
        title: 'review-agent',
        text: '我会先把这个目录作为当前上下文，不会影响其他目录下已有会话的消息记录。'
      }
    ];
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
