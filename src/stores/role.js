import { computed, reactive, ref } from 'vue';
import { defineStore } from 'pinia';
import { COMPRESSION_OPTIONS, DUTY_PRESETS, PROMPT_LAYER_OPTIONS, ROLE_LIBRARY } from '../data';

const ROLE_STATE_STORAGE_KEY = 'agentDesk.roleState.v1';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
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

export const useRoleStore = defineStore('role', () => {
  const storedState = readStoredRoleState();
  const roles = reactive({ ...clone(ROLE_LIBRARY), ...(storedState.roles || {}) });
  const activeRoleId = ref('primary');

  const activeRole = computed(() => getRole(activeRoleId.value));

  function persistState() {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(ROLE_STATE_STORAGE_KEY, JSON.stringify({ roles: clone(roles) }));
  }

  function getRole(roleId) {
    return roles[roleId] || roles.primary;
  }

  function setActiveRole(roleId) {
    activeRoleId.value = roles[roleId] ? roleId : 'primary';
  }

  function updateRole(roleId, patch) {
    Object.assign(getRole(roleId), patch);
    persistState();
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
    activeRole,
    dutyPresets: DUTY_PRESETS,
    compressionOptions: COMPRESSION_OPTIONS,
    promptLayerOptions: PROMPT_LAYER_OPTIONS,
    getRole,
    setActiveRole,
    updateRole,
    ensureThreadRole
  };
});
