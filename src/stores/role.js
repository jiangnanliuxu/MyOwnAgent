import { computed, reactive, ref } from 'vue';
import { defineStore } from 'pinia';
import { COMPRESSION_OPTIONS, DUTY_PRESETS, PROMPT_LAYER_OPTIONS, ROLE_LIBRARY } from '../data';
import { listProjectRoles, patchProjectRole } from '../api/roles';
import { apiSession, isBackendConfigured } from '../api/session';

const ROLE_STATE_STORAGE_KEY = 'agentDesk.roleState.v1';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function readStoredRoleState() {
  if (typeof window === 'undefined') return {};
  try {
    return JSON.parse(window.localStorage.getItem(ROLE_STATE_STORAGE_KEY) || '{}');
  } catch {
    return {};
  }
}

function buildThreadRole(context) {
  const isArranged = context.roleStatus !== '未编排';
  const folder = context.folder || context.file;
  return {
    id: context.sessionRoleId,
    name: context.label,
    tag: isArranged ? 'Session Role' : '未编排角色',
    alias: isArranged ? `${context.label}-session` : 'unarranged-session',
    description: context.summary,
    shortDescription: `${folder} / ${context.roleStatus || '未编排'}`,
    model: isArranged ? 'GPT-5.4-mini' : '待编排',
    provider: isArranged ? 'OpenAI' : '未选择',
    officialUrl: 'https://platform.openai.com/docs',
    apiKey: isArranged ? 'demo-key-session' : '待配置',
    endpoint: 'https://api.openai.com/v1/responses',
    apiFormat: 'OpenAI Responses',
    modelMapping: `${context.label} -> ${isArranged ? 'gpt-5.4-mini' : 'unassigned'}`,
    configJson: isArranged ? '{\n  "reasoning_effort": "low",\n  "temperature": 0.2\n}' : '{\n  "status": "unarranged"\n}',
    compression: isArranged ? '轻压缩' : '关闭压缩',
    promptPrefix: isArranged ? `围绕 ${folder} 目录的 ${context.label} 会话处理任务。` : '这是一个新建但未编排的会话角色，先补齐职责、模型和提示前缀。',
    routing: isArranged ? `该会话角色绑定 ${folder} 目录，用于承接 ${context.summary}。` : '未编排角色暂不参与自动路由，需要先完成模型、职责和 Prompt 设置。',
    routingChips: [context.roleStatus || '未编排', context.label],
    tools: isArranged ? ['读代码', '整理结论'] : ['待授权'],
    handoff: isArranged ? '将会话上下文整理给主助手，由主助手统一对外输出。' : '未编排前只保留为候选角色，不自动接手任务。',
    matrixCopy: isArranged ? `绑定 ${folder} 目录下的 ${context.label} 会话。` : `新增自 ${folder} 目录，尚未进入正式角色编排。`,
    duties: isArranged ? ['分析排查'] : [],
    customDuty: isArranged ? context.summary : '等待补充职责说明。',
    promptLayers: ['任务层'],
    sourceThreadId: context.id,
    sourceFile: context.file,
    sourceFolder: folder,
    roleStatus: context.roleStatus || '未编排',
    icon: `<svg viewBox="0 0 24 24"><rect x="5" y="5" width="14" height="14" rx="2"></rect><path d="M8 9h8"></path><path d="M8 13h5"></path><path d="M17 17l3 3"></path></svg>`
  };
}

function readConfig(config = {}, key, fallback = '') {
  return config?.[key] ?? fallback;
}

function mapBackendRole(role) {
  const config = role.config || {};
  const clientKey = role.client_key || role.clientKey || role.id;
  return {
    id: clientKey,
    backendId: role.id,
    clientKey,
    name: role.name,
    tag: role.tag,
    alias: role.alias,
    description: role.description,
    shortDescription: role.short_description || role.shortDescription || role.description,
    status: role.status,
    isBuiltin: role.is_builtin ?? role.isBuiltin,
    model: readConfig(config, 'model', '待配置'),
    provider: readConfig(config, 'provider', '未选择'),
    officialUrl: readConfig(config, 'official_url'),
    apiKey: config.secret_ref ? '已托管 secret_ref' : '',
    secretRef: readConfig(config, 'secret_ref'),
    endpoint: readConfig(config, 'endpoint'),
    apiFormat: readConfig(config, 'api_format'),
    modelMapping: readConfig(config, 'model_mapping'),
    configJson: readConfig(config, 'config_json', '{}'),
    compression: readConfig(config, 'compression', '轻压缩'),
    promptPrefix: readConfig(config, 'prompt_prefix'),
    routing: readConfig(config, 'routing'),
    routingChips: asArray(config.routing_chips),
    tools: asArray(config.tools),
    handoff: readConfig(config, 'handoff'),
    matrixCopy: readConfig(config, 'matrix_copy', role.short_description || role.shortDescription || ''),
    duties: asArray(config.duties),
    customDuty: readConfig(config, 'custom_duty', role.description || ''),
    promptLayers: asArray(config.prompt_layers),
    icon: readConfig(config, 'icon'),
    boundThreads: asArray(role.bound_threads || role.boundThreads)
  };
}

function roleToPatchRequest(role, rawPatch = {}) {
  const config = {
    model: role.model,
    provider: role.provider,
    official_url: role.officialUrl,
    endpoint: role.endpoint,
    api_format: role.apiFormat,
    model_mapping: role.modelMapping,
    config_json: role.configJson,
    compression: role.compression,
    prompt_prefix: role.promptPrefix,
    routing: role.routing,
    routing_chips: role.routingChips || [],
    tools: role.tools || [],
    handoff: role.handoff,
    matrix_copy: role.matrixCopy,
    duties: role.duties || [],
    custom_duty: role.customDuty,
    prompt_layers: role.promptLayers || [],
    icon: role.icon || '',
    secret_ref: role.secretRef
  };
  if (Object.prototype.hasOwnProperty.call(rawPatch, 'apiKey') && rawPatch.apiKey) {
    config.api_key = rawPatch.apiKey;
  }
  return {
    name: role.name,
    alias: role.alias,
    tag: role.tag,
    description: role.description,
    short_description: role.shortDescription,
    status: role.status,
    config
  };
}

export const useRoleStore = defineStore('role', () => {
  const storedState = readStoredRoleState();
  const roles = reactive({ ...clone(ROLE_LIBRARY), ...(storedState.roles || {}) });
  const activeRoleId = ref('primary');
  const backendReady = ref(false);
  const backendError = ref('');

  const activeRole = computed(() => getRole(activeRoleId.value));

  function persistState() {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(ROLE_STATE_STORAGE_KEY, JSON.stringify({ roles: clone(roles) }));
  }

  function getRole(roleId) {
    return roles[roleId] || Object.values(roles).find((role) => role.backendId === roleId || role.clientKey === roleId) || roles.primary;
  }

  function setActiveRole(roleId) {
    const role = getRole(roleId);
    activeRoleId.value = role?.id || 'primary';
  }

  function updateRole(roleId, patch) {
    const role = getRole(roleId);
    const backendMode = isBackendConfigured() && role?.backendId;
    const localPatch = backendMode && Object.prototype.hasOwnProperty.call(patch, 'apiKey')
      ? { ...patch, apiKey: patch.apiKey ? '已提交到后端密钥托管' : role.apiKey }
      : patch;
    Object.assign(role, localPatch);
    persistState();
    if (backendMode) {
      patchProjectRole(role.backendId, roleToPatchRequest(role, patch))
        .then((response) => {
          const updated = response?.role || response;
          if (updated) Object.assign(role, mapBackendRole(updated));
          backendError.value = '';
          persistState();
        })
        .catch((error) => {
          backendError.value = error.message || '角色保存失败';
        });
    }
    return role;
  }

  function applyBackendRoles(data) {
    const items = Array.isArray(data) ? data : data?.items || [];
    items.forEach((item) => {
      const role = mapBackendRole(item);
      roles[role.id] = { ...(roles[role.id] || {}), ...role };
    });
    backendReady.value = Boolean(items.length);
    persistState();
    return items.length;
  }

  async function hydrateFromBackend() {
    if (!isBackendConfigured()) return false;
    try {
      const { projectId } = apiSession();
      const data = await listProjectRoles(projectId, { includeThreadRoles: true });
      applyBackendRoles(data);
      backendError.value = '';
      return true;
    } catch (error) {
      backendReady.value = false;
      backendError.value = error.message || '角色加载失败';
      return false;
    }
  }

  function ensureThreadRole(context) {
    const roleId = context.sessionRoleId;
    if (!roles[roleId]) {
      roles[roleId] = buildThreadRole(context);
      persistState();
    } else {
      roles[roleId].sourceThreadId = context.id;
      roles[roleId].sourceFile = context.file;
      roles[roleId].sourceFolder = context.folder || context.file;
      roles[roleId].roleStatus = context.roleStatus || roles[roleId].roleStatus || '未编排';
      roles[roleId].shortDescription = `${roles[roleId].sourceFolder} / ${roles[roleId].roleStatus}`;
    }
    return roles[roleId];
  }

  return {
    roles,
    activeRoleId,
    backendReady,
    backendError,
    activeRole,
    dutyPresets: DUTY_PRESETS,
    compressionOptions: COMPRESSION_OPTIONS,
    promptLayerOptions: PROMPT_LAYER_OPTIONS,
    getRole,
    setActiveRole,
    updateRole,
    applyBackendRoles,
    hydrateFromBackend,
    ensureThreadRole
  };
});
