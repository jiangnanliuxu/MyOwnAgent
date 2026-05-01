const clock02 = document.getElementById("clock-02");
const ACTIVE_THREAD_STORAGE_KEY = "agentDesk.activeThreadId";

const ROLE_LIBRARY = {
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

const THREAD_CONTEXTS = {
  "session-review": {
    id: "session-review",
    label: "review-agent",
    file: "src/auth/useSession.ts",
    summary: "正在梳理 session 状态变更点",
    roles: ["primary", "review", "auth"],
    focusRole: "review"
  },
  "session-auth": {
    id: "session-auth",
    label: "auth-agent",
    file: "src/auth/useSession.ts",
    summary: "已标记 token 刷新与超时回收逻辑",
    roles: ["primary", "auth", "review"],
    focusRole: "auth"
  },
  "route-primary": {
    id: "route-primary",
    label: "route-agent",
    file: "src/router/guard.ts",
    summary: "关注未登录跳转与白名单路径",
    roles: ["primary", "route", "review", "test"],
    focusRole: "route"
  },
  "route-review": {
    id: "route-review",
    label: "review-agent",
    file: "src/router/guard.ts",
    summary: "正在检查守卫依赖的 session 来源",
    roles: ["primary", "review", "route", "test"],
    focusRole: "review"
  },
  "route-test": {
    id: "route-test",
    label: "test-agent",
    file: "src/router/guard.ts",
    summary: "准备生成重定向回归用例",
    roles: ["primary", "test", "route", "review"],
    focusRole: "test"
  },
  "login-test": {
    id: "login-test",
    label: "test-agent",
    file: "tests/auth-login.spec.ts",
    summary: "待补登录失败与重定向断言",
    roles: ["primary", "test", "snapshot"],
    focusRole: "test"
  },
  "login-snapshot": {
    id: "login-snapshot",
    label: "snapshot-agent",
    file: "tests/auth-login.spec.ts",
    summary: "正在核对认证流程截图变化",
    roles: ["primary", "snapshot", "test"],
    focusRole: "snapshot"
  }
};

const DUTY_PRESETS = ["分析排查", "验证回归", "视觉确认", "协调汇总"];
const COMPRESSION_OPTIONS = ["关闭压缩", "轻压缩", "中压缩", "强压缩"];
const PROMPT_LAYER_OPTIONS = ["系统层", "角色层", "任务层", "记忆层"];

const SKILL_CATALOG = [
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

const MCP_ENDPOINTS = [
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

const HEALTH_ITEMS = [
  ["Skill 装载", "正常", "3 个 skill 已进入候选池，按页面场景自动启用。"],
  ["MCP 连接", "正常", "当前接口可被页面配置引用，失败时会回到本地摘要。"],
  ["Prompt 挂载", "待确认", "新增 skill 默认不直接进入系统层，需要人工确认范围。"],
  ["密钥状态", "演示", "当前 API Key 为示例值，真实接入前需要替换。"]
];

function renderStageClock() {
  if (!clock02) {
    return;
  }

  const now = new Date();
  clock02.textContent = now.toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });
}

function getStoredThreadId() {
  const params = new URLSearchParams(window.location.search);
  const threadFromQuery = params.get("thread");

  if (threadFromQuery && THREAD_CONTEXTS[threadFromQuery]) {
    localStorage.setItem(ACTIVE_THREAD_STORAGE_KEY, threadFromQuery);
    return threadFromQuery;
  }

  const threadFromStorage = localStorage.getItem(ACTIVE_THREAD_STORAGE_KEY);
  if (threadFromStorage && THREAD_CONTEXTS[threadFromStorage]) {
    return threadFromStorage;
  }

  return "session-review";
}

function getPersistedThreadId() {
  const params = new URLSearchParams(window.location.search);
  const threadFromQuery = params.get("thread");

  if (threadFromQuery && THREAD_CONTEXTS[threadFromQuery]) {
    return threadFromQuery;
  }

  const threadFromStorage = localStorage.getItem(ACTIVE_THREAD_STORAGE_KEY);
  if (threadFromStorage && THREAD_CONTEXTS[threadFromStorage]) {
    return threadFromStorage;
  }

  return null;
}

function getContext(threadId) {
  return THREAD_CONTEXTS[threadId] || THREAD_CONTEXTS["session-review"];
}

function getRole(roleId) {
  return ROLE_LIBRARY[roleId] || ROLE_LIBRARY.primary;
}

function escapeHTML(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function updateRobotLinks(threadId = getPersistedThreadId()) {
  const validThreadId = threadId && THREAD_CONTEXTS[threadId] ? threadId : null;
  const isRobotSettingsPage = window.location.pathname.endsWith("/robot-settings.html");

  document.querySelectorAll('a[href^="./robot-settings.html"]').forEach((link) => {
    if (isRobotSettingsPage && link.classList.contains("is-active")) {
      link.href = "./robot-settings.html";
      return;
    }

    link.href = validThreadId ? `./robot-settings.html?thread=${encodeURIComponent(validThreadId)}` : "./robot-settings.html";
  });
}

function createToast(message) {
  let toast = document.getElementById("toast");
  if (!toast) {
    toast = document.createElement("div");
    toast.id = "toast";
    toast.className = "toast is-hidden";
    document.body.appendChild(toast);
  }

  toast.textContent = message;
  toast.classList.remove("is-hidden");
  window.clearTimeout(createToast.timer);
  createToast.timer = window.setTimeout(() => {
    toast.classList.add("is-hidden");
  }, 2200);
}

function createModalHost() {
  let host = document.getElementById("modal-host");
  if (host) {
    return host;
  }

  host = document.createElement("div");
  host.id = "modal-host";
  host.className = "modal-host is-hidden";
  host.innerHTML = `
    <div class="modal-backdrop" data-close-modal="true"></div>
    <section class="modal-sheet" role="dialog" aria-modal="true" aria-labelledby="modal-title">
      <div class="modal-head">
        <div>
          <strong id="modal-title">配置</strong>
          <span id="modal-subtitle">在这里补全当前配置。</span>
        </div>
        <button class="icon-button" id="modal-close-button" type="button" data-close-modal="true" data-tooltip="关闭">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6 18 18"></path>
            <path d="M18 6 6 18"></path>
          </svg>
        </button>
      </div>
      <div class="modal-body" id="modal-body"></div>
      <div class="modal-footer">
        <button class="tiny-action" id="modal-cancel-button" type="button" data-close-modal="true">取消</button>
        <button class="tiny-action modal-primary-action" id="modal-save-button" type="button">保存配置</button>
      </div>
    </section>
  `;
  document.body.appendChild(host);
  return host;
}

function initModalSystem() {
  const host = createModalHost();
  const title = host.querySelector("#modal-title");
  const subtitle = host.querySelector("#modal-subtitle");
  const body = host.querySelector("#modal-body");
  const saveButton = host.querySelector("#modal-save-button");

  function closeModal() {
    host.classList.add("is-hidden");
    body.innerHTML = "";
    saveButton.onclick = null;
  }

  if (!host.dataset.bound) {
    host.addEventListener("click", (event) => {
      const target = event.target instanceof Element ? event.target.closest("[data-close-modal='true']") : null;
      if (target) {
        closeModal();
      }
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !host.classList.contains("is-hidden")) {
        closeModal();
      }
    });

    host.dataset.bound = "true";
  }

  return {
    open({ modalTitle, modalSubtitle, content, saveLabel, onSave }) {
      title.textContent = modalTitle;
      subtitle.textContent = modalSubtitle;
      body.innerHTML = content;
      saveButton.textContent = saveLabel || "保存配置";
      saveButton.onclick = () => {
        if (onSave) {
          onSave(body);
        }
        closeModal();
      };
      host.classList.remove("is-hidden");
    },
    close: closeModal
  };
}

function initSectionDirectory() {
  const directoryLinks = Array.from(document.querySelectorAll(".settings-directory a[href^='#']"));

  if (!directoryLinks.length) {
    return;
  }

  const linkMap = directoryLinks
    .map((link) => {
      const hash = link.getAttribute("href");
      const section = hash ? document.querySelector(hash) : null;
      return section ? { link, hash, section } : null;
    })
    .filter(Boolean);

  if (!linkMap.length) {
    return;
  }

  function setActive(targetHash) {
    linkMap.forEach(({ link, hash }) => {
      link.classList.toggle("is-active", hash === targetHash);
    });
  }

  linkMap.forEach(({ link, hash, section }) => {
    link.addEventListener("click", (event) => {
      event.preventDefault();
      section.scrollIntoView({ behavior: "smooth", block: "start" });
      history.replaceState(null, "", hash);
      setActive(hash);
    });
  });

  const observer = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];

      if (!visible) {
        return;
      }

      const match = linkMap.find(({ section }) => section === visible.target);
      if (match) {
        setActive(match.hash);
      }
    },
    { root: null, rootMargin: "-18% 0px -55% 0px", threshold: [0.2, 0.45, 0.7] }
  );

  linkMap.forEach(({ section }) => observer.observe(section));
  const currentHash = window.location.hash;
  const initial = linkMap.find(({ hash }) => hash === currentHash) || linkMap[0];
  setActive(initial.hash);
}

function initThreadSelection() {
  const threadCards = Array.from(document.querySelectorAll(".agent-thread-card[data-thread-id]"));
  if (!threadCards.length) {
    updateRobotLinks(getPersistedThreadId());
    return;
  }

  const fileAccordions = Array.from(document.querySelectorAll(".file-accordion"));
  const activeFileTitle = document.getElementById("active-file-title");
  const composerThreadChip = document.getElementById("composer-thread-chip");
  let activeThreadId = getStoredThreadId();

  function syncSelection(nextThreadId) {
    const nextContext = getContext(nextThreadId);
    activeThreadId = nextContext.id;
    localStorage.setItem(ACTIVE_THREAD_STORAGE_KEY, activeThreadId);

    threadCards.forEach((card) => {
      card.classList.toggle("is-active", card.dataset.threadId === activeThreadId);
    });

    fileAccordions.forEach((accordion) => {
      const containsActive = Boolean(
        accordion.querySelector(`.agent-thread-card[data-thread-id="${activeThreadId}"]`)
      );
      accordion.classList.toggle("has-active-thread", containsActive);
      accordion.open = containsActive;
    });

    if (activeFileTitle) {
      activeFileTitle.textContent = nextContext.file;
    }

    if (composerThreadChip) {
      composerThreadChip.textContent = `当前线程 / ${nextContext.label}`;
    }

    updateRobotLinks(activeThreadId);
  }

  threadCards.forEach((card) => {
    card.addEventListener("click", () => {
      syncSelection(card.dataset.threadId);
    });
  });

  fileAccordions.forEach((accordion) => {
    const summary = accordion.querySelector("summary");
    const firstThreadCard = accordion.querySelector(".agent-thread-card[data-thread-id]");

    if (!summary || !firstThreadCard) {
      return;
    }

    summary.addEventListener("click", (event) => {
      if (!accordion.open) {
        event.preventDefault();
        syncSelection(firstThreadCard.dataset.threadId);
      }
    });

    summary.addEventListener("keydown", (event) => {
      if ((event.key === "Enter" || event.key === " ") && !accordion.open) {
        event.preventDefault();
        syncSelection(firstThreadCard.dataset.threadId);
      }
    });

    accordion.addEventListener("toggle", () => {
      const containsActive = Boolean(
        accordion.querySelector(`.agent-thread-card[data-thread-id="${activeThreadId}"]`)
      );
      accordion.classList.toggle("has-active-thread", containsActive);
    });
  });

  syncSelection(activeThreadId);
}

function initComposer() {
  const composerText = document.getElementById("composer-text");
  const sendButton = document.getElementById("composer-send");
  const sendHotkey = document.getElementById("composer-send-hotkey");
  const attachButton = document.getElementById("composer-attach-file");
  const terminalButton = document.getElementById("composer-insert-terminal");
  const stack = document.querySelector(".conversation-stack");
  const contextChip = document.getElementById("composer-context-chip");

  if (!composerText || !stack) {
    return;
  }

  function appendText(fragment) {
    const current = composerText.textContent.trim();
    composerText.textContent = current ? `${current}\n${fragment}` : fragment;
  }

  function appendBubble(kind, title, text) {
    const bubble = document.createElement("div");
    bubble.className = `bubble ${kind}`;
    bubble.innerHTML = `
      <strong>${escapeHTML(title)}</strong>
      <p>${escapeHTML(text)}</p>
    `;
    stack.appendChild(bubble);
    stack.scrollTop = stack.scrollHeight;
  }

  function sendMessage() {
    const text = composerText.textContent.trim();
    if (!text) {
      createToast("先输入一点内容再发送");
      return;
    }

    const context = getContext(getStoredThreadId());
    appendBubble("user", "你", text);
    appendBubble(
      "agent",
      "主助手",
      `已把这条请求绑定到 ${context.file} / ${context.label}。下一步会优先让 ${context.roles
        .map((roleId) => getRole(roleId).name)
        .join("、")} 接力处理。`
    );
    composerText.textContent = "";
    createToast("已加入当前会话");
  }

  sendButton?.addEventListener("click", sendMessage);
  sendHotkey?.addEventListener("click", sendMessage);

  composerText.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      sendMessage();
    }
  });

  attachButton?.addEventListener("click", () => {
    const context = getContext(getStoredThreadId());
    appendText(`附加文件：${context.file}`);
    if (contextChip) {
      contextChip.textContent = `已附加 ${context.file}`;
    }
    createToast("已附加当前文件");
  });

  terminalButton?.addEventListener("click", () => {
    appendText("终端输出：npm test -- --runInBand 仍待执行");
    if (contextChip) {
      contextChip.textContent = "已插入终端输出占位";
    }
    createToast("已插入终端输出占位");
  });
}

function initRoleWorkbench() {
  const roleDirectory = document.getElementById("agent-directory");
  if (!roleDirectory) {
    return;
  }

  const selectedThreadLabel = document.getElementById("selected-thread-label");
  const selectedThreadFile = document.getElementById("selected-thread-file");
  const selectedThreadCopy = document.getElementById("selected-thread-copy");
  const roleCount = document.getElementById("role-count");
  const contextRoleTotal = document.getElementById("context-role-total");
  const contextFile = document.getElementById("context-file");
  const contextThread = document.getElementById("context-thread");
  const activeRoleAvatar = document.getElementById("active-role-avatar");
  const activeRoleTag = document.getElementById("active-role-tag");
  const activeRoleName = document.getElementById("active-role-name");
  const activeRoleDescription = document.getElementById("active-role-description");
  const activeRoleTarget = document.getElementById("active-role-target");
  const activeRoleModel = document.getElementById("active-role-model");
  const activeRoleCompression = document.getElementById("active-role-compression");
  const activeRoleRouting = document.getElementById("active-role-routing");
  const routingChipRow = document.getElementById("routing-chip-row");
  const activeRoleTools = document.getElementById("active-role-tools");
  const activeRoleHandoff = document.getElementById("active-role-handoff");
  const activeRolePrefix = document.getElementById("active-role-prefix");
  const roleMatrix = document.getElementById("role-matrix");
  const aliasInput = document.getElementById("alias-input");
  const roleDutyOptions = document.getElementById("role-duty-options");
  const customDutyInput = document.getElementById("custom-duty-input");
  const promptLayerRow = document.getElementById("prompt-layer-row");
  const modelConfigButton = document.getElementById("open-model-config");
  const compressionConfigButton = document.getElementById("open-compression-config");
  const editRoleDescriptionButton = document.getElementById("edit-role-description");
  const editPromptPrefixButton = document.getElementById("edit-prompt-prefix");
  const copyRoleConfigButton = document.getElementById("copy-role-config");
  const modal = initModalSystem();

  let activeThreadId = getStoredThreadId();
  let activeRoleId = getContext(activeThreadId).focusRole;

  function renderChips(container, values, toneClass) {
    container.innerHTML = "";
    values.forEach((value) => {
      const span = document.createElement("span");
      span.className = toneClass;
      span.textContent = value;
      container.appendChild(span);
    });
  }

  function setActiveRole(roleId) {
    activeRoleId = roleId;
    renderWorkbench();
  }

  function renderRoleDirectory(context) {
    roleDirectory.innerHTML = "";

    context.roles.forEach((roleId) => {
      const role = getRole(roleId);
      const button = document.createElement("button");
      button.className = "directory-item agent-item";
      button.type = "button";
      button.classList.toggle("is-active", roleId === activeRoleId);
      button.innerHTML = `
        <span class="directory-icon" aria-hidden="true">${role.icon}</span>
        <strong>${escapeHTML(role.name)}</strong>
        <span>${escapeHTML(role.shortDescription)}</span>
      `;
      button.addEventListener("click", () => setActiveRole(roleId));
      roleDirectory.appendChild(button);
    });
  }

  function renderRoleMatrix(context) {
    roleMatrix.innerHTML = "";

    context.roles.forEach((roleId) => {
      const role = getRole(roleId);
      const button = document.createElement("button");
      button.className = "role-matrix-card";
      button.type = "button";
      button.classList.toggle("is-active", roleId === activeRoleId);
      button.innerHTML = `
        <strong>${escapeHTML(role.name)}</strong>
        <p>${escapeHTML(role.matrixCopy)}</p>
      `;
      button.addEventListener("click", () => setActiveRole(roleId));
      roleMatrix.appendChild(button);
    });
  }

  function renderDutyOptions(role) {
    roleDutyOptions.innerHTML = "";

    DUTY_PRESETS.forEach((duty) => {
      const label = document.createElement("label");
      label.className = "check-item";
      label.innerHTML = `
        <input type="checkbox" value="${escapeHTML(duty)}" ${role.duties.includes(duty) ? "checked" : ""} />
        <span>${escapeHTML(duty)}</span>
      `;
      label.querySelector("input").addEventListener("change", () => {
        const selected = Array.from(roleDutyOptions.querySelectorAll("input:checked")).map((input) => input.value);
        ROLE_LIBRARY[activeRoleId].duties = selected;
        createToast("职责已更新");
      });
      roleDutyOptions.appendChild(label);
    });

    customDutyInput.value = role.customDuty;
  }

  function renderWorkbench() {
    const context = getContext(activeThreadId);
    const role = getRole(activeRoleId);

    selectedThreadLabel.textContent = context.label;
    selectedThreadFile.textContent = context.file;
    selectedThreadCopy.textContent = context.summary;
    roleCount.textContent = `${context.roles.length} 个`;
    contextRoleTotal.textContent = `${context.roles.length} 个角色`;
    contextFile.textContent = context.file;
    contextThread.textContent = context.label;

    activeRoleAvatar.innerHTML = role.icon;
    activeRoleTag.textContent = role.tag;
    activeRoleName.textContent = role.name;
    activeRoleDescription.textContent = role.description;
    activeRoleTarget.textContent = role.name;
    activeRoleModel.textContent = role.model;
    activeRoleCompression.textContent = role.compression;
    activeRoleRouting.textContent = role.routing;
    activeRoleHandoff.textContent = role.handoff;
    activeRolePrefix.textContent = role.promptPrefix;
    aliasInput.value = role.alias;

    renderRoleDirectory(context);
    renderRoleMatrix(context);
    renderChips(routingChipRow, role.routingChips, "status-chip");
    renderChips(activeRoleTools, role.tools, "status-chip");
    renderChips(promptLayerRow, role.promptLayers, "status-chip");
    renderDutyOptions(role);
  }

  function openModelConfig() {
    const role = getRole(activeRoleId);
    modal.open({
      modalTitle: `${role.name} / 模型接入`,
      modalSubtitle: "补全供应商、官网、密钥、请求地址、格式映射和原始 JSON。",
      saveLabel: "保存模型配置",
      content: `
        <div class="modal-form-grid">
          <label class="modal-field">
            <span>供应商名称</span>
            <input class="text-input" id="provider-name-input" type="text" value="${escapeHTML(role.provider || "")}" />
          </label>
          <label class="modal-field">
            <span>官网链接</span>
            <input class="text-input" id="provider-url-input" type="text" value="${escapeHTML(role.officialUrl || "")}" />
          </label>
          <label class="modal-field modal-field-full">
            <span>API Key</span>
            <input class="text-input" id="provider-key-input" type="text" value="${escapeHTML(role.apiKey || "")}" />
          </label>
          <label class="modal-field modal-field-full">
            <span>请求地址</span>
            <input class="text-input" id="provider-endpoint-input" type="text" value="${escapeHTML(role.endpoint || "")}" />
          </label>
          <label class="modal-field">
            <span>API 格式</span>
            <select class="text-input" id="provider-format-input">
              ${["OpenAI Responses", "OpenAI Chat Completions", "Anthropic Messages", "Gemini GenerateContent"]
                .map((format) => `<option ${role.apiFormat === format ? "selected" : ""}>${format}</option>`)
                .join("")}
            </select>
          </label>
          <label class="modal-field">
            <span>模型名</span>
            <input class="text-input" id="provider-model-input" type="text" value="${escapeHTML(role.model || "")}" />
          </label>
          <label class="modal-field modal-field-full">
            <span>模型映射</span>
            <textarea class="text-input modal-textarea" id="provider-mapping-input">${escapeHTML(role.modelMapping || "")}</textarea>
          </label>
          <label class="modal-field modal-field-full">
            <span>配置 JSON</span>
            <textarea class="text-input modal-textarea modal-codearea" id="provider-json-input">${escapeHTML(role.configJson || "")}</textarea>
          </label>
        </div>
      `,
      onSave(modalBody) {
        role.provider = modalBody.querySelector("#provider-name-input").value.trim();
        role.officialUrl = modalBody.querySelector("#provider-url-input").value.trim();
        role.apiKey = modalBody.querySelector("#provider-key-input").value.trim();
        role.endpoint = modalBody.querySelector("#provider-endpoint-input").value.trim();
        role.apiFormat = modalBody.querySelector("#provider-format-input").value;
        role.model = modalBody.querySelector("#provider-model-input").value.trim();
        role.modelMapping = modalBody.querySelector("#provider-mapping-input").value.trim();
        role.configJson = modalBody.querySelector("#provider-json-input").value;
        renderWorkbench();
        createToast("模型配置已更新");
      }
    });
  }

  function openCompressionConfig() {
    const role = getRole(activeRoleId);
    const compressionOptions = COMPRESSION_OPTIONS.map(
      (option) => `
        <label class="choice-row">
          <input type="radio" name="compression-level" value="${option}" ${role.compression === option ? "checked" : ""} />
          <span>${option}</span>
        </label>
      `
    ).join("");

    modal.open({
      modalTitle: `${role.name} / 压缩强度`,
      modalSubtitle: "选择这个角色默认使用的上下文压缩强度。",
      saveLabel: "保存压缩强度",
      content: `<div class="choice-stack">${compressionOptions}</div>`,
      onSave(modalBody) {
        const nextCompression = modalBody.querySelector('input[name="compression-level"]:checked');
        if (nextCompression) {
          role.compression = nextCompression.value;
          renderWorkbench();
          createToast("压缩强度已保存");
        }
      }
    });
  }

  function openRoleDescriptionEditor() {
    const role = getRole(activeRoleId);
    modal.open({
      modalTitle: `${role.name} / 角色说明`,
      modalSubtitle: "修改角色名称、职责说明和矩阵里的展示文案。",
      saveLabel: "保存角色说明",
      content: `
        <div class="modal-form-grid">
          <label class="modal-field">
            <span>角色名称</span>
            <input class="text-input" id="role-name-input" type="text" value="${escapeHTML(role.name)}" />
          </label>
          <label class="modal-field">
            <span>别名</span>
            <input class="text-input" id="role-alias-input" type="text" value="${escapeHTML(role.alias)}" />
          </label>
          <label class="modal-field modal-field-full">
            <span>职责说明</span>
            <textarea class="text-input modal-textarea" id="role-description-input">${escapeHTML(role.description)}</textarea>
          </label>
          <label class="modal-field modal-field-full">
            <span>矩阵说明</span>
            <textarea class="text-input modal-textarea" id="role-matrix-input">${escapeHTML(role.matrixCopy)}</textarea>
          </label>
        </div>
      `,
      onSave(modalBody) {
        role.name = modalBody.querySelector("#role-name-input").value.trim() || role.name;
        role.alias = modalBody.querySelector("#role-alias-input").value.trim() || role.alias;
        role.description = modalBody.querySelector("#role-description-input").value.trim() || role.description;
        role.matrixCopy = modalBody.querySelector("#role-matrix-input").value.trim() || role.matrixCopy;
        renderWorkbench();
        createToast("角色说明已更新");
      }
    });
  }

  function openPromptPrefixEditor() {
    const role = getRole(activeRoleId);
    const layerOptions = PROMPT_LAYER_OPTIONS.map(
      (layer) => `
        <label class="choice-row">
          <input type="checkbox" name="prompt-layer" value="${layer}" ${role.promptLayers.includes(layer) ? "checked" : ""} />
          <span>${layer}</span>
        </label>
      `
    ).join("");

    modal.open({
      modalTitle: `${role.name} / 提示前缀`,
      modalSubtitle: "控制这个角色在接任务前先看到什么边界提示。",
      saveLabel: "保存提示前缀",
      content: `
        <div class="modal-form-grid">
          <label class="modal-field modal-field-full">
            <span>提示前缀</span>
            <textarea class="text-input modal-textarea" id="prompt-prefix-input">${escapeHTML(role.promptPrefix)}</textarea>
          </label>
          <div class="modal-field modal-field-full">
            <span>注入位置</span>
            <div class="choice-stack">${layerOptions}</div>
          </div>
        </div>
      `,
      onSave(modalBody) {
        const selectedLayers = Array.from(modalBody.querySelectorAll('input[name="prompt-layer"]:checked')).map(
          (input) => input.value
        );
        role.promptPrefix = modalBody.querySelector("#prompt-prefix-input").value.trim() || role.promptPrefix;
        role.promptLayers = selectedLayers.length ? selectedLayers : role.promptLayers;
        renderWorkbench();
        createToast("提示前缀已更新");
      }
    });
  }

  function openRoleConfigCopy() {
    const role = getRole(activeRoleId);
    const config = JSON.stringify(
      {
        role: role.name,
        alias: role.alias,
        model: role.model,
        provider: role.provider,
        compression: role.compression,
        tools: role.tools,
        promptPrefix: role.promptPrefix,
        routing: role.routing
      },
      null,
      2
    );

    modal.open({
      modalTitle: `${role.name} / 当前配置`,
      modalSubtitle: "这里展示的是可复制的角色配置快照。",
      saveLabel: "复制完成",
      content: `
        <label class="modal-field modal-field-full">
          <span>配置 JSON</span>
          <textarea class="text-input modal-textarea modal-codearea" id="role-config-output">${escapeHTML(config)}</textarea>
        </label>
      `,
      onSave() {
        createToast("配置快照已准备好");
      }
    });
  }

  aliasInput.addEventListener("input", () => {
    ROLE_LIBRARY[activeRoleId].alias = aliasInput.value.trim() || ROLE_LIBRARY[activeRoleId].alias;
  });

  customDutyInput.addEventListener("input", () => {
    ROLE_LIBRARY[activeRoleId].customDuty = customDutyInput.value;
  });

  modelConfigButton?.addEventListener("click", openModelConfig);
  compressionConfigButton?.addEventListener("click", openCompressionConfig);
  editRoleDescriptionButton?.addEventListener("click", openRoleDescriptionEditor);
  editPromptPrefixButton?.addEventListener("click", openPromptPrefixEditor);
  copyRoleConfigButton?.addEventListener("click", openRoleConfigCopy);

  document.querySelector(".agent-identity")?.addEventListener("click", (event) => {
    const actionButton = event.target instanceof Element ? event.target.closest("button") : null;
    if (!actionButton) {
      return;
    }

    if (actionButton.id === "open-model-config") {
      openModelConfig();
    }

    if (actionButton.id === "open-compression-config") {
      openCompressionConfig();
    }

    if (actionButton.id === "edit-role-description") {
      openRoleDescriptionEditor();
    }

    if (actionButton.id === "edit-prompt-prefix") {
      openPromptPrefixEditor();
    }

    if (actionButton.id === "copy-role-config") {
      openRoleConfigCopy();
    }
  });

  updateRobotLinks(activeThreadId);
  renderWorkbench();
}

function initContextControlActions() {
  const contextPanel = document.getElementById("context-ops");
  const platformPanel = document.getElementById("platform-entry");
  const modal = initModalSystem();

  if (contextPanel) {
    const actionButtons = Array.from(contextPanel.querySelectorAll("[data-modal]"));

    actionButtons.forEach((button) => {
      button.addEventListener("click", () => {
        const modalType = button.dataset.modal;

        if (modalType === "compression-settings") {
          const options = COMPRESSION_OPTIONS.map(
            (option, index) => `
              <label class="choice-row">
                <input type="radio" name="workspace-compression" value="${option}" ${index === 2 ? "checked" : ""} />
                <span>${option}</span>
              </label>
            `
          ).join("");

          modal.open({
            modalTitle: "上下文压缩",
            modalSubtitle: "触发压缩时可以先选强度，再决定保留哪些内容。",
            saveLabel: "执行压缩",
            content: `
              <div class="modal-form-grid">
                <div class="modal-field modal-field-full">
                  <span>压缩强度</span>
                  <div class="choice-stack">${options}</div>
                </div>
                <label class="modal-field modal-field-full">
                  <span>保留内容</span>
                  <textarea class="text-input modal-textarea">当前文件、最近操作链、角色分工、关键结论</textarea>
                </label>
              </div>
            `,
            onSave() {
              createToast("已触发上下文压缩");
            }
          });
        }

        if (modalType === "backup-settings") {
          modal.open({
            modalTitle: "上下文内容备份",
            modalSubtitle: "压缩前先生成快照，决定备份哪些内容以及落在哪里。",
            saveLabel: "生成备份",
            content: `
              <div class="modal-form-grid">
                <label class="modal-field modal-field-full">
                  <span>备份内容</span>
                  <textarea class="text-input modal-textarea">重要对话、阶段结论、角色 Prompt、交付摘要</textarea>
                </label>
                <label class="modal-field">
                  <span>备份位置</span>
                  <input class="text-input" type="text" value="workspace-memory/context-snapshots/" />
                </label>
                <label class="modal-field">
                  <span>命名规则</span>
                  <input class="text-input" type="text" value="thread-id + stage + timestamp" />
                </label>
              </div>
            `,
            onSave() {
              createToast("备份快照已生成");
            }
          });
        }
      });
    });
  }

  if (platformPanel) {
    const cards = {
      "open-task-queue": ["任务队列", "排队中：截图核验、MCP 健康检查。阻塞：真实 API Key 待配置。运行中：当前页面状态验证。"],
      "open-run-log": ["运行日志", "最近动作：恢复 script.js、渲染 Skill/MCP 页面、同步当前会话到机器人设置。"],
      "open-tool-permission": ["工具授权", "默认允许本地读写当前项目；外部提交、权限变更和敏感数据传输仍需要用户确认。"],
      "open-memory-backup": ["记忆与备份", "当前备份策略：压缩前生成阶段快照，保留角色 Prompt、会话摘要和交付结论。"]
    };

    Object.entries(cards).forEach(([id, [title, copy]]) => {
      const button = document.getElementById(id);
      button?.addEventListener("click", () => {
        modal.open({
          modalTitle: title,
          modalSubtitle: "这里先作为平台入口原型展示，后续可接真实列表。",
          saveLabel: "知道了",
          content: `<div class="value-box modal-field-full">${escapeHTML(copy)}</div>`
        });
      });
    });
  }
}

function initSimpleControls() {
  document.querySelectorAll(".segmented").forEach((group) => {
    group.querySelectorAll(".seg-chip").forEach((button) => {
      button.addEventListener("click", () => {
        group.querySelectorAll(".seg-chip").forEach((chip) => chip.classList.remove("is-active"));
        button.classList.add("is-active");
        createToast(`${button.textContent.trim()} 已选中`);
      });
    });
  });
}

function initIntegrationManagement() {
  const skillCatalog = document.getElementById("skill-catalog");
  const skillMountGrid = document.getElementById("skill-mount-grid");
  const endpointList = document.getElementById("mcp-endpoint-list");
  const healthList = document.getElementById("integration-health-list");

  if (!skillCatalog || !endpointList) {
    return;
  }

  const modal = initModalSystem();
  const skillCountLabel = document.getElementById("skill-count-label");
  const endpointCountLabel = document.getElementById("endpoint-count-label");
  const latestSkillChange = document.getElementById("latest-skill-change");

  function renderSkills() {
    skillCountLabel.textContent = `${SKILL_CATALOG.length} 个已装载`;
    latestSkillChange.textContent = `${SKILL_CATALOG[0].name} 已接入`;
    skillCatalog.innerHTML = "";
    skillMountGrid.innerHTML = "";

    SKILL_CATALOG.forEach((skill) => {
      const card = document.createElement("article");
      card.className = "catalog-card";
      card.innerHTML = `
        <div class="catalog-card-head">
          <strong>${escapeHTML(skill.name)}</strong>
          <span class="status-chip">${escapeHTML(skill.status)}</span>
        </div>
        <p>${escapeHTML(skill.scope)}</p>
        <div class="meta-grid">
          <span>来源</span><strong>${escapeHTML(skill.source)}</strong>
          <span>挂载</span><strong>${escapeHTML(skill.mounts.join(" / "))}</strong>
          <span>最近</span><strong>${escapeHTML(skill.lastRun)}</strong>
        </div>
        <div class="card-action-row">
          <button class="tiny-action" type="button" data-skill-toggle="${skill.id}">${skill.status === "启用" ? "停用" : "启用"}</button>
          <button class="tiny-action" type="button" data-skill-config="${skill.id}">配置</button>
        </div>
      `;
      skillCatalog.appendChild(card);

      const mount = document.createElement("div");
      mount.className = "mount-card";
      mount.innerHTML = `
        <strong>${escapeHTML(skill.name)}</strong>
        <p>${escapeHTML(skill.mounts.join("、"))}</p>
        <span>${escapeHTML(skill.scope)}</span>
      `;
      skillMountGrid.appendChild(mount);
    });

    skillCatalog.querySelectorAll("[data-skill-toggle]").forEach((button) => {
      button.addEventListener("click", () => {
        const skill = SKILL_CATALOG.find((item) => item.id === button.dataset.skillToggle);
        skill.status = skill.status === "启用" ? "停用" : "启用";
        renderSkills();
        createToast(`${skill.name} 已${skill.status}`);
      });
    });

    skillCatalog.querySelectorAll("[data-skill-config]").forEach((button) => {
      button.addEventListener("click", () => {
        const skill = SKILL_CATALOG.find((item) => item.id === button.dataset.skillConfig);
        openSkillModal(skill);
      });
    });
  }

  function renderEndpoints() {
    endpointCountLabel.textContent = `${MCP_ENDPOINTS.length} 条接口`;
    endpointList.innerHTML = "";

    MCP_ENDPOINTS.forEach((endpoint) => {
      const item = document.createElement("article");
      item.className = "endpoint-card";
      item.innerHTML = `
        <div class="endpoint-main">
          <strong>${escapeHTML(endpoint.name)}</strong>
          <p>${escapeHTML(endpoint.tools)}</p>
          <div class="meta-grid">
            <span>传输</span><strong>${escapeHTML(endpoint.transport)}</strong>
            <span>认证</span><strong>${escapeHTML(endpoint.auth)}</strong>
            <span>地址</span><strong>${escapeHTML(endpoint.url)}</strong>
          </div>
        </div>
        <div class="endpoint-side">
          <span class="status-chip">${escapeHTML(endpoint.status)}</span>
          <span class="light-meta">${escapeHTML(endpoint.latency)}</span>
          <button class="tiny-action" type="button" data-endpoint-check="${endpoint.id}">检查</button>
          <button class="tiny-action" type="button" data-endpoint-edit="${endpoint.id}">编辑</button>
        </div>
      `;
      endpointList.appendChild(item);
    });

    endpointList.querySelectorAll("[data-endpoint-check]").forEach((button) => {
      button.addEventListener("click", () => {
        const endpoint = MCP_ENDPOINTS.find((item) => item.id === button.dataset.endpointCheck);
        endpoint.status = "已连接";
        endpoint.latency = `${Math.floor(30 + Math.random() * 120)}ms`;
        renderEndpoints();
        renderHealth();
        createToast(`${endpoint.name} 检查完成`);
      });
    });

    endpointList.querySelectorAll("[data-endpoint-edit]").forEach((button) => {
      button.addEventListener("click", () => {
        const endpoint = MCP_ENDPOINTS.find((item) => item.id === button.dataset.endpointEdit);
        openEndpointModal(endpoint);
      });
    });
  }

  function renderHealth() {
    healthList.innerHTML = "";
    HEALTH_ITEMS.forEach(([name, state, copy]) => {
      const item = document.createElement("div");
      item.className = "health-item";
      item.innerHTML = `
        <strong>${escapeHTML(name)}</strong>
        <span class="status-chip">${escapeHTML(state)}</span>
        <p>${escapeHTML(copy)}</p>
      `;
      healthList.appendChild(item);
    });
  }

  function openSkillModal(skill) {
    const target = skill || { id: "", name: "", source: "local skill", status: "启用", scope: "", mounts: ["任务层"], lastRun: "新建" };
    modal.open({
      modalTitle: skill ? `${skill.name} / Skill 配置` : "新增 Skill",
      modalSubtitle: "配置 skill 来源、启用状态和 Prompt 挂载位置。",
      saveLabel: skill ? "保存 Skill" : "新增 Skill",
      content: `
        <div class="modal-form-grid">
          <label class="modal-field">
            <span>Skill 名称</span>
            <input class="text-input" id="skill-name-input" type="text" value="${escapeHTML(target.name)}" />
          </label>
          <label class="modal-field">
            <span>来源</span>
            <input class="text-input" id="skill-source-input" type="text" value="${escapeHTML(target.source)}" />
          </label>
          <label class="modal-field modal-field-full">
            <span>使用范围</span>
            <textarea class="text-input modal-textarea" id="skill-scope-input">${escapeHTML(target.scope)}</textarea>
          </label>
          <label class="modal-field">
            <span>状态</span>
            <select class="text-input" id="skill-status-input">
              <option ${target.status === "启用" ? "selected" : ""}>启用</option>
              <option ${target.status === "停用" ? "selected" : ""}>停用</option>
              <option ${target.status === "待启用" ? "selected" : ""}>待启用</option>
            </select>
          </label>
          <label class="modal-field">
            <span>挂载层</span>
            <input class="text-input" id="skill-mount-input" type="text" value="${escapeHTML(target.mounts.join(", "))}" />
          </label>
        </div>
      `,
      onSave(modalBody) {
        target.name = modalBody.querySelector("#skill-name-input").value.trim() || target.name || "custom-skill";
        target.id = target.id || target.name.toLowerCase().replaceAll(" ", "-");
        target.source = modalBody.querySelector("#skill-source-input").value.trim() || "local skill";
        target.scope = modalBody.querySelector("#skill-scope-input").value.trim() || "自定义能力";
        target.status = modalBody.querySelector("#skill-status-input").value;
        target.mounts = modalBody
          .querySelector("#skill-mount-input")
          .value.split(",")
          .map((item) => item.trim())
          .filter(Boolean);
        target.lastRun = target.lastRun || "新建";
        if (!skill) {
          SKILL_CATALOG.unshift(target);
        }
        renderSkills();
        createToast("Skill 配置已保存");
      }
    });
  }

  function openEndpointModal(endpoint) {
    const target =
      endpoint || {
        id: "",
        name: "",
        transport: "stdio",
        status: "待连接",
        auth: "local",
        url: "mcp://",
        tools: "",
        latency: "未检查"
      };
    modal.open({
      modalTitle: endpoint ? `${endpoint.name} / MCP 接口` : "新增 MCP 接口",
      modalSubtitle: "配置接口名称、传输方式、认证方式、地址和工具映射。",
      saveLabel: endpoint ? "保存接口" : "新增接口",
      content: `
        <div class="modal-form-grid">
          <label class="modal-field">
            <span>接口名称</span>
            <input class="text-input" id="endpoint-name-input" type="text" value="${escapeHTML(target.name)}" />
          </label>
          <label class="modal-field">
            <span>传输方式</span>
            <input class="text-input" id="endpoint-transport-input" type="text" value="${escapeHTML(target.transport)}" />
          </label>
          <label class="modal-field">
            <span>认证方式</span>
            <input class="text-input" id="endpoint-auth-input" type="text" value="${escapeHTML(target.auth)}" />
          </label>
          <label class="modal-field">
            <span>状态</span>
            <select class="text-input" id="endpoint-status-input">
              <option ${target.status === "已连接" ? "selected" : ""}>已连接</option>
              <option ${target.status === "待连接" ? "selected" : ""}>待连接</option>
              <option ${target.status === "异常" ? "selected" : ""}>异常</option>
            </select>
          </label>
          <label class="modal-field modal-field-full">
            <span>请求地址 / 标识</span>
            <input class="text-input" id="endpoint-url-input" type="text" value="${escapeHTML(target.url)}" />
          </label>
          <label class="modal-field modal-field-full">
            <span>工具映射</span>
            <textarea class="text-input modal-textarea" id="endpoint-tools-input">${escapeHTML(target.tools)}</textarea>
          </label>
        </div>
      `,
      onSave(modalBody) {
        target.name = modalBody.querySelector("#endpoint-name-input").value.trim() || target.name || "Custom MCP";
        target.id = target.id || target.name.toLowerCase().replaceAll(" ", "-");
        target.transport = modalBody.querySelector("#endpoint-transport-input").value.trim() || "stdio";
        target.auth = modalBody.querySelector("#endpoint-auth-input").value.trim() || "local";
        target.status = modalBody.querySelector("#endpoint-status-input").value;
        target.url = modalBody.querySelector("#endpoint-url-input").value.trim() || "mcp://";
        target.tools = modalBody.querySelector("#endpoint-tools-input").value.trim() || "custom_tool";
        if (!endpoint) {
          MCP_ENDPOINTS.unshift(target);
        }
        renderEndpoints();
        renderHealth();
        createToast("MCP 接口已保存");
      }
    });
  }

  document.getElementById("open-install-skill")?.addEventListener("click", () => openSkillModal(null));
  document.getElementById("open-sync-skill-policy")?.addEventListener("click", () => {
    createToast("装载策略已同步");
    renderSkills();
  });
  document.getElementById("open-add-endpoint")?.addEventListener("click", () => openEndpointModal(null));
  document.getElementById("open-endpoint-check")?.addEventListener("click", () => {
    MCP_ENDPOINTS.forEach((endpoint, index) => {
      endpoint.status = "已连接";
      endpoint.latency = `${42 + index * 31}ms`;
    });
    renderEndpoints();
    renderHealth();
    createToast("批量健康检查完成");
  });

  renderSkills();
  renderEndpoints();
  renderHealth();
}

if (clock02) {
  renderStageClock();
  setInterval(renderStageClock, 1000);
}

updateRobotLinks();
initSectionDirectory();
initSimpleControls();
initThreadSelection();
initComposer();
initRoleWorkbench();
initContextControlActions();
initIntegrationManagement();
