export const ACTIVE_THREAD_STORAGE_KEY = "agentDesk.activeThreadId";

export const ROLE_LIBRARY = {
  primary: {
    id: "primary",
    name: "主助手",
    tag: "Primary Agent",
    alias: "orchestrator",
    description: "负责拆解任务、调度子 agent、把最后结果整理给用户。",
    shortDescription: "总控调度、拆解问题、汇总对外输出。",
    model: "GPT-5.4",
    provider: "OpenAI",
    officialUrl: "https://platform.openai.com/docs",
    apiKey: "demo-key-orchestrator",
    endpoint: "https://api.openai.com/v1/responses",
    apiFormat: "OpenAI Responses",
    modelMapping: "orchestrator -> gpt-5.4",
    configJson: '{\n  "reasoning_effort": "medium",\n  "temperature": 0.2\n}',
    compression: "中压缩",
    promptPrefix: "先拆解任务，再调度子 agent，最后只输出用户真正需要的结论。",
    routing: "复杂协调交给主助手，跨文件信息和最终对外表达都由它统一收口。",
    routingChips: ["主助手 / GPT-5.4", "跨角色汇总"],
    tools: ["读代码", "执行测试", "整理结论"],
    handoff: "把其他角色的摘要汇总成面对用户的一次输出，不丢上下文。",
    matrixCopy: "负责拉齐上下文、拆解问题和最后汇总，不替代专项角色的局部判断。",
    duties: ["协调汇总"],
    customDuty: "需要时协调多角色结论冲突。",
    promptLayers: ["系统层", "角色层", "任务层"],
    icon: `<svg viewBox="0 0 24 24"><rect x="6" y="8" width="12" height="10" rx="2"></rect><path d="M12 4v4"></path><path d="M9 12h.01"></path><path d="M15 12h.01"></path><path d="M9 16h6"></path></svg>`
  },
  review: {
    id: "review",
    name: "review-agent",
    tag: "Review Agent",
    alias: "review-guard",
    description: "负责变更审查、影响面排查和回归建议。",
    shortDescription: "变更审查、影响面排查、回归建议。",
    model: "GPT-5.4-mini",
    provider: "OpenAI",
    officialUrl: "https://platform.openai.com/docs",
    apiKey: "demo-key-review",
    endpoint: "https://api.openai.com/v1/responses",
    apiFormat: "OpenAI Responses",
    modelMapping: "review-agent -> gpt-5.4-mini",
    configJson: '{\n  "reasoning_effort": "low",\n  "temperature": 0.1\n}',
    compression: "轻压缩",
    promptPrefix: "先定位风险，再给出最短可执行建议；证据不足时明确标出不确定点。",
    routing: "读 diff、扫影响面和测试缺口时优先交给 review-agent，输出结构化风险摘要。",
    routingChips: ["review-agent / Mini", "风险归类"],
    tools: ["读代码", "对比变更", "列回归点"],
    handoff: "只回传风险和缺口，不替主助手直接给最终答复。",
    matrixCopy: "偏静态分析和风险归类，默认不改代码，只指出影响点与缺口。",
    duties: ["分析排查", "验证回归"],
    customDuty: "补充测试遗漏提醒。",
    promptLayers: ["系统层", "角色层"],
    icon: `<svg viewBox="0 0 24 24"><path d="M9 3h6"></path><path d="M10 7h4"></path><rect x="5" y="5" width="14" height="16" rx="2"></rect><path d="m9 12 2 2 4-4"></path></svg>`
  },
  route: {
    id: "route",
    name: "route-agent",
    tag: "Route Agent",
    alias: "route-guard",
    description: "专注登录守卫、白名单路径和重定向流向。",
    shortDescription: "登录守卫、白名单路径、重定向流向。",
    model: "GPT-5.4-mini",
    provider: "OpenAI",
    officialUrl: "https://platform.openai.com/docs",
    apiKey: "demo-key-route",
    endpoint: "https://api.openai.com/v1/responses",
    apiFormat: "OpenAI Responses",
    modelMapping: "route-agent -> gpt-5.4-mini",
    configJson: '{\n  "reasoning_effort": "low",\n  "temperature": 0.15\n}',
    compression: "轻压缩",
    promptPrefix: "只追登录守卫、白名单和重定向链路，不扩散到 UI 细节。",
    routing: "涉及跳转链路、鉴权拦截和白名单判断时，优先切给 route-agent 追路径。",
    routingChips: ["route-agent / Mini", "路径追踪"],
    tools: ["读代码", "执行测试", "梳理跳转"],
    handoff: "把路径结论和边界条件回传给主助手，再决定如何组织说明。",
    matrixCopy: "专注登录守卫、重定向和白名单路径，不扩散到 UI 细节。",
    duties: ["分析排查", "验证回归"],
    customDuty: "补充未登录边界回放。",
    promptLayers: ["系统层", "任务层"],
    icon: `<svg viewBox="0 0 24 24"><circle cx="6" cy="18" r="2"></circle><circle cx="18" cy="6" r="2"></circle><path d="M8 18h3a5 5 0 0 0 5-5V8"></path></svg>`
  },
  test: {
    id: "test",
    name: "test-agent",
    tag: "Test Agent",
    alias: "smoke-check",
    description: "负责最小验证顺序、用例优先级和断言缺口。",
    shortDescription: "用例生成、smoke 顺序、断言补齐。",
    model: "GPT-5.4-mini",
    provider: "Anthropic",
    officialUrl: "https://docs.anthropic.com",
    apiKey: "demo-key-test",
    endpoint: "https://api.anthropic.com/v1/messages",
    apiFormat: "Anthropic Messages",
    modelMapping: "test-agent -> claude-sonnet",
    configJson: '{\n  "max_tokens": 4096,\n  "temperature": 0.1\n}',
    compression: "轻压缩",
    promptPrefix: "优先给最小验证顺序，再补断言缺口和失败回放线索。",
    routing: "当问题已经收窄到验证顺序和断言补齐时，测试角色优先接手。",
    routingChips: ["test-agent / Mini", "验证顺序"],
    tools: ["执行测试", "列断言", "整理 smoke"],
    handoff: "输出最小验证顺序和断言缺口，交给主助手统一组织优先级。",
    matrixCopy: "负责最小验证顺序、用例优先级与断言缺口，不主导需求拆分。",
    duties: ["验证回归"],
    customDuty: "补充失败用例描述。",
    promptLayers: ["角色层", "任务层"],
    icon: `<svg viewBox="0 0 24 24"><path d="M9 3h6"></path><path d="M10 3v5l-5 9a3 3 0 0 0 2.6 4h8.8A3 3 0 0 0 19 17l-5-9V3"></path></svg>`
  },
  snapshot: {
    id: "snapshot",
    name: "snapshot-agent",
    tag: "Snapshot Agent",
    alias: "visual-check",
    description: "负责界面快照、差异记录和视觉确认。",
    shortDescription: "界面快照、差异记录、视觉确认。",
    model: "GPT-5.4-mini",
    provider: "OpenAI",
    officialUrl: "https://platform.openai.com/docs",
    apiKey: "demo-key-snapshot",
    endpoint: "https://api.openai.com/v1/responses",
    apiFormat: "OpenAI Responses",
    modelMapping: "snapshot-agent -> gpt-5.4-mini",
    configJson: '{\n  "reasoning_effort": "low",\n  "temperature": 0.0\n}',
    compression: "轻压缩",
    promptPrefix: "只描述可见状态、差异和截图证据，避免替代业务判断。",
    routing: "当要确认截图变化、界面差异和视觉收口时，快照角色负责把视觉信息拉平。",
    routingChips: ["snapshot-agent / Mini", "视觉确认"],
    tools: ["截图比对", "视觉核验", "整理差异"],
    handoff: "只回传视觉变化和状态确认，不替代功能结论。",
    matrixCopy: "专注视觉对比和页面状态核验，帮助前端收口，不主导业务判断。",
    duties: ["视觉确认"],
    customDuty: "补充页面差异说明。",
    promptLayers: ["系统层", "角色层"],
    icon: `<svg viewBox="0 0 24 24"><rect x="3" y="6" width="18" height="14" rx="2"></rect><circle cx="12" cy="13" r="3"></circle><path d="M8 6 9.5 4h5L16 6"></path></svg>`
  },
  auth: {
    id: "auth",
    name: "auth-agent",
    tag: "Auth Agent",
    alias: "token-watch",
    description: "负责 token 刷新、超时回收和认证链路状态判断。",
    shortDescription: "token 刷新、超时回收、认证状态。",
    model: "GPT-5.4-mini",
    provider: "OpenAI",
    officialUrl: "https://platform.openai.com/docs",
    apiKey: "demo-key-auth",
    endpoint: "https://api.openai.com/v1/responses",
    apiFormat: "OpenAI Responses",
    modelMapping: "auth-agent -> gpt-5.4-mini",
    configJson: '{\n  "reasoning_effort": "medium",\n  "temperature": 0.1\n}',
    compression: "轻压缩",
    promptPrefix: "只围绕认证状态、token 生命周期和 session 回收判断边界。",
    routing: "认证状态切换、token 生命周期和 session 回收问题优先交给 auth-agent。",
    routingChips: ["auth-agent / Mini", "认证链路"],
    tools: ["读代码", "排查状态", "列认证边界"],
    handoff: "把认证状态变化点交回主助手，再决定是否要扩散到路由或测试。",
    matrixCopy: "专注 token 生命周期、session 状态和认证边界，不负责页面呈现。",
    duties: ["分析排查", "验证回归"],
    customDuty: "补充登录态恢复策略。",
    promptLayers: ["角色层", "任务层"],
    icon: `<svg viewBox="0 0 24 24"><path d="M12 3 5 7v5c0 4.2 2.9 8.1 7 9 4.1-.9 7-4.8 7-9V7l-7-4Z"></path><path d="m9.5 12 1.7 1.7 3.3-3.3"></path></svg>`
  }
};

export const THREAD_CONTEXTS = {
  "session-review": {
    id: "session-review",
    label: "review-agent",
    file: "src/auth/useSession.ts",
    folder: "src/auth",
    summary: "正在梳理 session 状态变更点",
    roles: ["primary", "review", "auth"],
    focusRole: "review"
  },
  "session-auth": {
    id: "session-auth",
    label: "auth-agent",
    file: "src/auth/useSession.ts",
    folder: "src/auth",
    summary: "已标记 token 刷新与超时回收逻辑",
    roles: ["primary", "auth", "review"],
    focusRole: "auth"
  },
  "route-primary": {
    id: "route-primary",
    label: "route-agent",
    file: "src/router/guard.ts",
    folder: "src/router",
    summary: "关注未登录跳转与白名单路径",
    roles: ["primary", "route", "review", "test"],
    focusRole: "route"
  },
  "route-review": {
    id: "route-review",
    label: "review-agent",
    file: "src/router/guard.ts",
    folder: "src/router",
    summary: "正在检查守卫依赖的 session 来源",
    roles: ["primary", "review", "route", "test"],
    focusRole: "review"
  },
  "route-test": {
    id: "route-test",
    label: "test-agent",
    file: "src/router/guard.ts",
    folder: "src/router",
    summary: "准备生成重定向回归用例",
    roles: ["primary", "test", "route", "review"],
    focusRole: "test"
  },
  "login-test": {
    id: "login-test",
    label: "test-agent",
    file: "tests/auth-login.spec.ts",
    folder: "tests",
    summary: "待补登录失败与重定向断言",
    roles: ["primary", "test", "snapshot"],
    focusRole: "test"
  },
  "login-snapshot": {
    id: "login-snapshot",
    label: "snapshot-agent",
    file: "tests/auth-login.spec.ts",
    folder: "tests",
    summary: "正在核对认证流程截图变化",
    roles: ["primary", "snapshot", "test"],
    focusRole: "snapshot"
  }
};

export const DUTY_PRESETS = ["分析排查", "验证回归", "视觉确认", "协调汇总"];
export const COMPRESSION_OPTIONS = ["关闭压缩", "轻压缩", "中压缩", "强压缩"];
export const PROMPT_LAYER_OPTIONS = ["系统层", "角色层", "任务层", "记忆层"];

export const SKILL_CATALOG = [
  {
    id: "frontend-design",
    name: "frontend-design",
    source: "local skill",
    status: "启用",
    scope: "页面设计、交互细节、前端视觉 polish",
    mounts: ["系统层", "任务层"],
    lastRun: "04:02 / 当前原型"
  },
  {
    id: "browser-use",
    name: "browser-use",
    source: "plugin skill",
    status: "启用",
    scope: "本地页面打开、点击、截图和可视验证",
    mounts: ["任务层"],
    lastRun: "03:42 / 弹窗验证"
  },
  {
    id: "openai-docs",
    name: "openai-docs",
    source: "system skill",
    status: "待启用",
    scope: "OpenAI API 文档、模型升级和 Prompt 迁移",
    mounts: ["任务层"],
    lastRun: "未触发"
  }
];

export const MCP_ENDPOINTS = [
  {
    id: "figma",
    name: "Figma MCP",
    transport: "plugin api",
    status: "已连接",
    auth: "OAuth",
    url: "mcp://codex-apps/figma",
    tools: "design_context, use_figma, generate_diagram",
    latency: "128ms"
  },
  {
    id: "node-repl",
    name: "Node REPL",
    transport: "stdio",
    status: "已连接",
    auth: "local",
    url: "mcp://node-repl/js",
    tools: "js, js_reset",
    latency: "22ms"
  },
  {
    id: "browser",
    name: "Browser Use",
    transport: "iab",
    status: "已连接",
    auth: "local session",
    url: "file:// / localhost",
    tools: "goto, click, screenshot, domSnapshot",
    latency: "76ms"
  }
];

export const HEALTH_ITEMS = [
  ["Skill 装载", "正常", "3 个 skill 已进入候选池，按页面场景自动启用。"],
  ["MCP 连接", "正常", "当前接口可被页面配置引用，失败时会回到本地摘要。"],
  ["Prompt 挂载", "待确认", "新增 skill 默认不直接进入系统层，需要人工确认范围。"],
  ["密钥状态", "演示", "当前 API Key 为示例值，真实接入前需要替换。"]
];
