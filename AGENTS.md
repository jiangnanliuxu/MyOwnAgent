# Agent Desk 项目认知

## 项目定位

这个项目已经从静态多页面原型迁移为 Vite + Vue 3 工程化前端，用来表达 agent 工作台、系统设置、机器人角色配置、Skill 与 MCP 能力管理等产品流程。

当前后端方案已经在 `BACKEND-ARCHITECTURE.md` 中明确：首选 Spring Boot / Spring AI + Python。Spring Boot 作为 BFF、控制面、安全边界、MCP/Skill 治理和 SSE 推送入口；Python 负责 Agent 编排、Skill Runner、RAG 文档解析/检索和 Python 生态 MCP Server。Milvus 作为 RAG 向量库，PostgreSQL/Redis/MinIO 作为业务数据、事件队列和原始文件存储。

当前底层技术栈：

- Vite 6：开发服务器、构建与 Vue SFC 编译。
- Vue 3.5 + Composition API：4 个业务页面迁移为 route view。
- Vue Router 4：`/`、`/settings`、`/robot-settings`、`/integration` 四条主路由。
- Pinia：承载线程、角色、Skill、MCP mock 状态。
- CSS：`src/assets/global.css` 复用原视觉风格、设计 token、布局与响应式规则。
- Playwright：`tests/` 下覆盖 4 个页面的端到端测试。
- Vitest：`__tests__/` 下覆盖 store 核心逻辑。

## 项目结构

- `index.html`：Vite 入口 HTML，挂载 `src/main.js`。
- `legacy-index.html`：迁移前主工作台静态页备份。
- `settings.html`、`robot-settings.html`、`integration-settings.html`、`script.js`、`styles.css`：迁移前静态实现，保留作对照，不作为新功能主要维护入口。
- `vite.config.js`：Vite 配置，包含 Vue 插件、`@` alias、4173 端口和 SPA fallback。
- `playwright.config.js`：Playwright 配置，测试默认访问 `http://localhost:4173` 并自动启动 `npm run dev`。
- `BACKEND-ARCHITECTURE.md`：后端架构主文档，包含 Spring AI + Python 方案、REST/SSE/API 契约、RAG、MCP、Skill、数据模型、部署和前端接入细节。
- `BACKEND-DEVELOPMENT-LOG.md`：后端分段开发日志，记录 Planning/Development/Testing 三个子 Agent 的阶段进度、开发勾选、测试勾选和 bug 修复闭环。
- `src/data/index.js`：从原 `script.js` 提取的 mock 数据常量。
- `src/stores/`：Pinia stores，包含 thread、role、skill、mcp。
- `src/composables/`：modal、toast、clock、目录滚动、localStorage 等共享逻辑。
- `src/components/layout/`：顶部导航、时钟等布局组件。
- `src/components/shared/`：全局 modal host 与 toast。
- `src/views/`：4 个页面 view。
- `__tests__/`：Vitest store 单元测试。
- `tests/`：Playwright E2E 测试目录。

后端接入时建议新增但当前尚未实现的前端目录：

- `src/api/`：封装后端 API adapter，包括 `http.js`、`bootstrap.js`、`threads.js`、`roles.js`、`skills.js`、`mcp.js`、`rag.js`、`settings.js`。
- `src/services/`：封装浏览器侧业务适配，包括 `sseClient.js`、`uploadQueue.js`、`idempotency.js`、`threadHydrator.js`。
- 接后端时不要让 Vue 页面直接散落 `fetch`；页面调用 Pinia store，store 再调用 `src/api`。

## 路由职责

### `/`

主工作台负责展示当前项目上下文：

- 左侧目录 accordion 中只能有一个 agent 会话处于高亮状态。
- 当前高亮会话写入 `localStorage`，并驱动机器人设置链接的 `thread` 参数。
- “新增关联目录”通过浏览器原生文件夹选择器触发系统目录选择能力，选中目录后会新增关联目录并创建默认会话。
- 每个目录下可以新增独立会话；每条会话拥有自己的消息流，切换会话时右侧对话内容同步切换。
- 目录 accordion 支持展开、折叠和键盘操作；展开一个折叠目录时会选中该目录下第一个会话。
- Composer 支持发送、快捷发送、附加当前目录和插入终端输出占位。
- 输入框下方文件按钮 `#composer-attach-file` 是后端接入后的 RAG 上传索引入口。当前前端实现仍是 `attachFolder()` 插入“附加目录”，后端接入时应升级为打开文件选择器并调用 `POST /api/v1/threads/:id/rag/uploads`。
- 侧边栏“新增关联目录”只负责创建 folder/thread，不触发 RAG 索引；不要把它和输入框下方文件按钮混用。

### `/settings`

系统设置页负责平台级配置入口：

- 设置目录使用锚点滚动并同步 `is-active`。
- 分段控件点击后切换当前选项并弹 toast。
- 上下文压缩和上下文内容备份通过 modal 配置并触发 toast。
- 平台逻辑入口包含任务队列、运行日志、工具授权、记忆与备份。

### `/robot-settings`

机器人设置页跟随当前会话：

- `?thread=` 参数优先，其次读 `localStorage`，最后回退到默认会话。
- 左侧角色编排、当前角色卡、模型与工具路由、角色矩阵同步当前目录下的会话角色集合。
- 当前角色支持模型接入、压缩强度、角色说明、提示前缀、配置快照等 modal。
- 新增机器人入口提供别名输入、预置职责复选框、自定义职责、Prompt 注入层展示。
- 目录下每条会话都会映射为一个可编辑角色；新增会话默认是“未编排角色”，可在此补齐模型、职责和 Prompt。
- 修改会话角色的名称和说明时，会同步更新对应 thread 的 `label` 和 `summary`，因此工作台和机器人设置保持同一份会话语义。

### `/integration`

能力管理页负责 agent 能力来源和接口边界：

- Skill 管理展示来源、启用状态、使用范围、挂载层和最近运行。
- Prompt 装载展示 skill 如何进入系统层、角色层、任务层等挂载点。
- MCP 接口管理展示接口名称、传输方式、认证方式、地址、工具映射、健康检查和编辑入口。
- 运行健康展示 Skill、MCP、Prompt 挂载、密钥状态等摘要。

## 核心数据与交互模型

- `src/data/index.js` 是 mock 数据来源，包含 `ROLE_LIBRARY`、`THREAD_CONTEXTS`、`SKILL_CATALOG`、`MCP_ENDPOINTS`、`HEALTH_ITEMS` 等。
- `useThreadStore` 管理当前线程、关联目录列表、目录下会话、每条会话的独立消息流、query/localStorage/default 优先级。
- `useThreadStore` 使用 `agentDesk.activeThreadId` 持久化当前线程，并用 `agentDesk.threadState.v1` 持久化新增目录、动态会话和会话消息；旧的文件路径会自动折算为目录，兼容历史 localStorage。
- `useRoleStore` 管理角色库、当前角色、职责、压缩强度和角色配置更新，并用 `agentDesk.roleState.v1` 持久化动态会话角色修改。
- `useRoleStore.ensureThreadRole(context)` 会把 thread 映射为会话角色：预置会话默认为 `Session Role`，新增会话默认为 `未编排角色`。
- `useSkillStore` 管理 Skill 启停、配置和新增。
- `useMcpStore` 管理 MCP 端点检查、批量健康检查和新增接口。
- `useModal` 与 `ModalHost.vue` 提供共享 modal，支持关闭按钮、遮罩、Escape、保存回调。
- `useToast` 与 `ToastMessage.vue` 提供共享 toast。
- `vite.config.js` 中的 `agentDeskSpaFallback()` 很重要：因为旧 `settings.html` 等文件仍保留，Vite dev server 会把 `/settings` clean URL 映射到旧 HTML；该中间件确保 `/settings`、`/robot-settings`、`/integration` 进入 Vue SPA。

## 后端架构认知

后端首选路线：

- Spring Boot 3.x + Spring AI：前端 API、认证、权限、事务、REST、SSE、MCP/Skill Registry、Tool Gateway、Embedding Gateway。
- Python FastAPI + LangGraph：Agent 编排、多角色调度、上下文压缩、Skill Runner、RAG Retriever。
- Milvus：RAG 向量数据库，默认 collection 为 `agent_desk_chunks`。
- PostgreSQL 16：核心业务表，包括 users、projects、folders、threads、messages、roles、skills、mcp_endpoints、rag_documents、rag_chunks、task_logs 等。
- Redis Streams：`agent.jobs`、`agent.events:{threadId}`、`rag.index.jobs`。
- MinIO/S3：RAG 原始文件、备份快照、会话导出。

关键边界：

- 浏览器只访问 Spring `/api/v1/**`，不直连 Python、Milvus、Redis 或 MCP Server。
- Python 默认不直接写核心业务表，状态通过 Redis Streams 或 Spring 内部 API 回传。
- Python 调工具必须经过 Spring `/internal/tools/invoke`，由 Spring 做鉴权、限流和审计。
- Embedding 密钥由 Spring 管理；Python 通过 `/internal/embeddings/embed` 获取向量，不持久化真实 key。
- 真实 API key 不落库明文，只保存 `secret_ref` 或脱敏摘要。

RAG 固定流程：

1. 用户点击输入框下方 `#composer-attach-file`。
2. 前端上传文件到 `POST /api/v1/threads/:id/rag/uploads`。
3. Spring 保存原始文件到 MinIO/S3，写入 `rag_documents` 和 `rag_index_jobs`。
4. Spring 发布 `rag.index.jobs`。
5. Python 解析文档、chunk、调用 Spring embedding、写入 Milvus。
6. 用户发送消息时 Python 先检索 Milvus，再把召回片段加入 prompt。

消息流固定流程：

1. 前端 `POST /api/v1/threads/:id/messages`，带 `client_message_id` 和 `X-Idempotency-Key`。
2. Spring 写 user message，发布 agent job。
3. Python 消费 `agent.jobs`，执行 LangGraph。
4. Python 写 `agent.events:{threadId}`。
5. Spring 用 SSE `GET /api/v1/threads/:id/stream` 推送 `message_start`、`rag_retrieval`、`text_delta`、`tool_call`、`tool_result`、`agent_handoff`、`message_complete`。
6. 前端按 SSE 事件更新气泡，支持 `Last-Event-ID` 断点续传。

## 后端三子 Agent 开发流程

后端项目必须拆成多个阶段分段开发，并由三个子 Agent 协作推进：

- Planning Agent：负责拆分阶段、确认范围、定义验收标准、安排下一步，并维护 `BACKEND-DEVELOPMENT-LOG.md`。
- Development Agent：负责当前阶段代码实现、数据库迁移、配置更新和 bug 修复。
- Testing Agent：负责当前阶段测试设计、测试执行、回归验证和 bug 复测。

强制闸门规则：

- 同一时间只允许一个后端阶段处于开发中。
- 每个阶段必须先由 Planning Agent 确认范围和验收标准。
- Development Agent 完成实现后，在 `BACKEND-DEVELOPMENT-LOG.md` 中勾选该阶段 `Dev Done`，并记录变更文件和自测命令。
- Testing Agent 必须在 Development Agent 完成后测试；测试通过后勾选 `Test Done`。
- 如果 Testing Agent 发现 bug，当前阶段不能进入下一步；必须交回 Development Agent 修复，修复后 Testing Agent 复测。
- 只有 `Dev Done` 和 `Test Done` 都完成，且 Planning Agent 批准后，才能进入下一阶段。
- 每个阶段完成后必须立即提交并推送到 Git：提交信息使用阶段编号开头或明确包含阶段编号，例如 `Add B04 bootstrap API`；推送成功并确认工作区干净后，默认自动进入下一阶段。
- 如果阶段提交或推送失败，当前阶段保持未关闭，必须先修复 Git/远端问题，不得继续开发下一阶段。
- 自动进入下一阶段时，Planning Agent 必须立即把 `BACKEND-DEVELOPMENT-LOG.md` 的当前状态切到下一阶段 `In Progress`，补充该阶段 Planning 记录，并在会话窗口说明当前切换到 Planning Agent 工作。
- 只有用户明确要求暂停、只提交不继续或等待确认时，阶段完成后才不自动进入下一阶段；暂停原因必须记录到 `BACKEND-DEVELOPMENT-LOG.md`。
- 每次阶段推进、bug 修复、测试失败或测试通过，都要更新 `BACKEND-DEVELOPMENT-LOG.md`，方便查看当前开发位置。
- 同一个 Gradle 工程的测试和构建不能并行执行；`backend-spring` 的 `./gradlew clean test`、dev profile 测试、`bootJar` 必须顺序运行，避免多个 Gradle 任务同时写同一个 `build/` 目录造成假失败。
- 主工作台会话窗口必须显示当前工作 Agent；切换 Planning / Development / Testing 状态时，不得影响当前 thread 选择和消息列表。

后端阶段默认从 `BACKEND-DEVELOPMENT-LOG.md` 的 B01 开始推进。当前拆分为 Spring Boot 骨架、基础设施与数据库、认证偏好、bootstrap、thread/message、role、skill、MCP、SSE/Agent job、Python Agent、RAG 索引、RAG 检索、多 Agent 编排、系统设置、前端 API 接入、观测安全部署等阶段。

## 开发与测试

建议使用 Node 18+。本机已验证可用 Node 版本：

```bash
PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH node -v
```

常用命令：

```bash
PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run dev
PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run build
PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:unit
PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:e2e
```

开发地址：

```text
http://localhost:4173/
```

截至 2026-05-02，已验证：

- `npm run build` 通过。
- `npm run test:unit`：4 个 Vitest 文件、6 个 store 用例通过。
- `npm run test:e2e`：101 个 Playwright 用例通过。
- `cd backend-spring && ./gradlew clean test`：7 个 Spring Boot 测试通过。
- `cd backend-spring && ./gradlew bootJar` 通过。

## 代码风格

- Vue SFC、CSS、JavaScript 使用两空格缩进。
- UI 文案以中文为主，除非需求明确要求修改，不要随意替换产品文案。
- CSS 类名沿用 kebab-case，公共视觉值放在 `:root` 自定义属性中。
- JavaScript 变量和函数使用 camelCase，常量使用大写蛇形命名。
- 新增页面行为优先落到 Vue view、Pinia store 或 composable，不要继续扩展 legacy `script.js`。
- 不要引入 Element Plus、Ant Design 等组件库，当前 UI 风格应继续保持现有定制视觉语言。

## 安全与配置

`src/data/index.js` 和 legacy `script.js` 中的 API key、供应商地址、MCP 地址都是界面演示用 mock 数据。不要把真实密钥写入仓库，不要把用户真实凭据替换进这些字段。

后端设计和前端接入也必须遵守：

- 不在文档、代码、测试 fixture 中写真实 API key、真实服务 token 或真实用户文件路径。
- RAG 上传文件的原文只进 MinIO/S3；Milvus 只存 chunk、向量和必要 metadata。
- Milvus 查询必须带 `project_id`，默认再带 `thread_id`，避免跨项目或跨会话召回。
- 对用户发起的写操作保留幂等 key，避免重试造成重复消息、重复任务或重复索引。

## 维护注意事项

- 修改线程选择逻辑时，要同时考虑 `/robot-settings?thread=...` 的联动，以及 `agentDesk.threadState.v1` 中的动态会话。
- 修改机器人设置页时，要保持同目录会话角色、当前角色、角色矩阵三者同步，并确认 `sourceThreadId` 角色编辑会回写 thread。
- 修改 settings 或 integration 的目录结构时，要同步检查 `.settings-directory` 锚点和对应 section id。
- 改动 modal 结构时，要回归取消、遮罩、Escape、保存 toast 四类交互。
- 若保留 legacy 静态文件，不能移除 `vite.config.js` 里的 SPA fallback，否则 `/settings` 等 Vue 路由会被旧 HTML 截胡。
- 修改后端相关设计时，优先更新 `BACKEND-ARCHITECTURE.md`，再同步本文件中的项目认知摘要。
- 修改 RAG 入口时，要保持“输入框下方文件按钮负责上传索引、侧边栏新增关联目录负责 folder/thread”的边界。
- 接入真实后端时，先实现 `bootstrap` 只读初始化，再替换 thread/role/skill/mcp 写操作，最后接 Agent SSE、RAG 和 MCP 健康检查。
