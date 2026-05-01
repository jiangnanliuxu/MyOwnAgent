# Agent Desk 后端架构设计方案

> 基于前端所有 mock 数据、Store 操作和用户交互反推
> 生成日期：2026-05-01

---

## 目录

1. [项目现状与后端接入边界](#1-项目现状与后端接入边界)
2. [整体架构](#2-整体架构)
3. [后端架构方案选择](#3-后端架构方案选择)
4. [技术选型](#4-技术选型)
5. [数据库设计](#5-数据库设计)
   - 5.4 [查询模式与附加索引](#54-查询模式与附加索引)
   - 5.5 [数据初始化（Flyway Migration）](#55-数据初始化flyway-migration)
   - 5.6 [状态枚举与前端展示映射](#56-状态枚举与前端展示映射)
6. [API 接口设计](#6-api-接口设计)
   - 6.6 [通用响应格式与错误处理](#66-通用响应格式与错误处理)
   - 6.7 [完整接口定义](#67-完整接口定义)
   - 6.8 [Redis Streams 事件协议](#68-redis-streams-事件协议)
7. [核心服务设计](#7-核心服务设计)
   - 7.5 [Spring Boot 配置参考](#75-spring-boot-配置参考)
   - 7.6 [Python 服务配置参考](#76-python-服务配置参考)
   - 7.7 [密钥管理](#77-密钥管理)
   - 7.8 [RAG 检索与索引服务](#78-rag-检索与索引服务)
   - 7.9 [Spring ↔ Python 内部 API 契约](#79-spring--python-内部-api-契约)
8. [实时通信方案](#8-实时通信方案)
9. [多 Agent 编排引擎](#9-多-agent-编排引擎)
   - 9.4 [错误恢复与重试机制](#94-错误恢复与重试机制)
10. [MCP 协议网关](#10-mcp-协议网关)
11. [认证与权限](#11-认证与权限)
12. [部署架构](#12-部署架构)
13. [开发路线图建议](#13-开发路线图建议)
14. [测试策略](#14-测试策略)
15. [监控与可观测性](#15-监控与可观测性)
16. [安全加固清单](#16-安全加固清单)
17. [项目流程与端到端请求生命周期](#17-项目流程与端到端请求生命周期)
18. [完整项目目录结构](#18-完整项目目录结构)
19. [本地开发环境搭建指南](#19-本地开发环境搭建指南)
20. [前端接入契约与用户交互细节](#20-前端接入契约与用户交互细节)

---

## 1. 项目现状与后端接入边界

当前项目是已经完成 Vue 3 工程化迁移的 Agent Desk 前端原型，核心运行形态是 Vite + Vue Router + Pinia。后端的第一目标不是重新定义产品，而是把 `src/data/index.js` 和 `src/stores/` 中的 mock 状态替换成可持久化、可审计、可流式执行的服务端能力。

### 1.1 当前前端事实

| 页面 | 当前职责 | 后端接入边界 |
|------|----------|--------------|
| `/` 主工作台 | 目录 accordion、会话切换、独立消息流、composer 发送、输入框下方文件按钮、附加目录和终端输出占位 | 提供项目、目录、会话、消息历史、消息发送、RAG 上传索引和 SSE 输出 |
| `/settings` 系统设置 | 平台级配置入口、上下文压缩、备份、运行日志与授权入口 | 提供用户配置、上下文压缩策略、备份快照、任务队列和运行日志 |
| `/robot-settings` 机器人设置 | 根据当前 thread 展示同目录会话角色，编辑模型、职责、Prompt、压缩强度 | 提供角色库、thread-role 关联、角色配置保存、密钥引用和 prompt mount policy |
| `/integration` 能力管理 | Skill 启停、新增、挂载策略、MCP 端点配置和健康检查 | 提供 Skill 管理、MCP endpoint 管理、工具注册表和健康检查记录 |

### 1.2 需要替换的 Store

| 前端 Store | 当前状态来源 | 后端承接对象 | 注意点 |
|------------|--------------|--------------|--------|
| `useThreadStore` | `THREAD_CONTEXTS`、`agentDesk.activeThreadId`、`agentDesk.threadState.v1` | `folders`、`threads`、`messages`、用户偏好 | 保持目录分组、会话独立消息流和动态新增会话 |
| `useRoleStore` | `ROLE_LIBRARY`、`agentDesk.roleState.v1` | `roles`、`thread_roles`、`roles.config` | 保持 `ensureThreadRole(context)` 的未编排角色占位逻辑 |
| `useSkillStore` | `SKILL_CATALOG` | `skills`、prompt mount policy | 保持来源、启停状态、挂载层和最近运行信息 |
| `useMcpStore` | `MCP_ENDPOINTS`、`HEALTH_ITEMS` | `mcp_endpoints`、`mcp_health_checks`、tool registry | 保持 transport、auth、tools、latency 和健康摘要 |

### 1.3 线程解析与兼容规则

前端当前线程解析规则必须在后端接入后保持一致：

1. URL `?thread=` 参数优先，且只有服务端确认可访问的 thread id 才生效。
2. 没有有效 query thread 时，读取用户偏好中的 `activeThreadId`，替代当前 `localStorage` 的 `agentDesk.activeThreadId`。
3. 两者都无效时回退默认会话 `session-review`，用于演示数据和首屏兜底。

后端接入期间，允许前端继续保留 `localStorage` 作为离线兜底，但服务端返回的当前用户偏好是最终状态来源。

### 1.4 v1 接入原则

- 先把 mock 数据搬到后端并提供只读初始化接口，避免一次性引入编排、MCP 和认证复杂度。
- 再接入会话新增、消息发送、角色编辑、Skill/MCP 配置保存等用户可见写操作。
- RAG 文档索引复用主工作台输入框下方现有 `#composer-attach-file` 按钮，不新增独立上传入口；按钮后端接入后从“附加当前目录”升级为“上传并索引当前会话资料”。
- 最后接入 Agent 编排、SSE 流式输出、RAG 检索注入和 MCP 健康检查真实执行。
- 真实 API Key 不写入仓库、不落库明文；后端只保存 `secret_ref`，由 Spring 密钥管理服务解析。

---

## 2. 整体架构

下图是目标架构边界。v1 采用 Spring Boot/Spring AI 作为主后端与控制面，Python 负责 Agent 编排、Skill 执行和 Python 生态 MCP Server。Spring 是系统的安全边界、数据一致性边界和工具治理入口；Python 是 AI/Skill 执行面。

```
┌──────────────────────────────────────────────────────────────┐
│                      Vue 3 SPA 前端                           │
│                   (localhost:4173)                            │
└──────────┬──────────┬──────────┬──────────┬──────────────────┘
           │ REST     │ SSE      │ WebSocket│
           ▼          ▼          ▼          ▼
┌──────────────────────────────────────────────────────────────┐
│                    API Gateway (Nginx / Kong)                  │
│                 路由、限流、认证、负载均衡                        │
└──────────────────────────┬───────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Spring BFF   │  │ Python Agent │  │ MCP/Skill    │
│ + 控制面      │  │ 执行服务       │  │ 执行层        │
│              │  │              │  │              │
│ - REST CRUD  │  │ - 多Agent调度 │  │ - MCP Server │
│ - SSE 推送    │  │ - LLM 调用    │  │ - Skill Run  │
│ - 角色管理    │  │ - Skill 编排  │  │ - 工具调用    │
│ - MCP治理    │  │ - 上下文压缩   │  │ - 健康检查    │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │                 │                 │
       ▼                 ▼                 ▼
┌──────────────────────────────────────────────────────────────┐
│                      数据层                                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐ │
│  │PostgreSQL│  │  Redis   │  │  MinIO   │  │  Vector DB   │ │
│  │ 业务数据  │  │ 缓存/队列 │  │ 文件存储  │  │  (Milvus)    │ │
│  └──────────┘  └──────────┘  └──────────┘  │ 向量检索/RAG  │ │
│                                             └──────────────┘ │
└──────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    外部 LLM 供应商                             │
│         OpenAI  │  Anthropic  │  Google  │  ...              │
└──────────────────────────────────────────────────────────────┘
```

### 为什么分三层

| 层 | 职责 | 原因 |
|---|------|------|
| Spring BFF / 控制面 | REST API、SSE、认证、权限、配置、MCP/Skill Registry | 前端只对接 Spring，所有数据写入、权限和工具调用审计统一收口 |
| Python Agent 服务 | 多 Agent 调度、LLM 调用、Skill Runner、上下文压缩、RAG 检索 | AI 执行逻辑用 Python 生态快速迭代，与 CRUD 和安全边界解耦 |
| MCP/Skill 执行层 | Python FastMCP / Java Spring AI MCP Server、具体工具和资源暴露 | MCP 协议和 Skill 执行有独立生命周期，可按工具类型独立部署 |

---

## 3. 后端架构方案选择

后端方案需要同时满足两个目标：第一阶段快速替换前端 mock store，后续又能抽出 Agent 编排、Skill Runner 和 MCP Server。结合团队偏好，首选 **Spring Boot/Spring AI + Python**：Spring 做主后端和治理控制面，Python 做 AI/Skill 执行面。

### 3.1 方案矩阵

| 方案 | 形态 | 技术组合 | 适用阶段 | 优点 | 风险 |
|------|------|----------|----------|------|------|
| 方案 A：Spring AI + Python | Spring Boot 主后端 + Python Agent/Skill 服务 | Spring Boot 3.x、Spring AI、PostgreSQL、Redis Streams、Python FastAPI、LangGraph、FastMCP | **v1 首选** | 匹配 Java/Python 技术栈；Spring 统一认证、事务、MCP/Skill 治理；Python 适合 Agent、Skill 和工具生态 | 双语言需要清晰内部协议和事件边界 |
| 方案 B：Node.js 全栈 | BFF、Agent、MCP 都用 TypeScript | Fastify/NestJS、BullMQ、Vercel AI SDK | 前端同语言团队 | 类型共享顺滑，部署简单 | 不符合当前 Java/Python 技术偏好，复杂 Agent/RAG 生态弱于 Python |
| 方案 C：Go + Python 异构 | Go 做高并发网关，Python 做 AI 编排 | Go、FastAPI、Kafka/Redis | 大流量或大团队后期 | 网关性能强，服务可独立扩缩容 | 当前阶段没有 Go 熟练度，复杂度收益不成比例 |
| 方案 D：Serverless/BaaS | 托管数据库、函数和实时通道 | Supabase/Neon/Vercel/Upstash | 临时 demo | 启动快，运维少 | MCP stdio、长任务、内网工具调用和密钥代理受限制 |

### 3.2 推荐路线

| 阶段 | 推荐方案 | 目标 |
|------|----------|------|
| v1 | Spring 模块化单体控制面 + Python Agent 服务 | 搬迁 mock 数据，完成 CRUD、用户偏好、消息发送、SSE、基础 Agent job |
| v1.5 | Python Skill Runner + FastMCP Server 独立化 | Skill 执行和 MCP 工具按 Python 服务拆出，Spring 仍统一治理 |
| v2 | Spring MCP Gateway / Registry 独立部署 | endpoint 配置、工具发现、健康检查、调用审计独立扩缩容 |
| v3 | 只在明确需要时引入 Go 或其他网关 | 高并发连接或组织边界需要时再替换局部网关 |

当前阶段选择 **方案 A：Spring AI + Python**。它能保留现有 REST/SSE/API 设计和 PostgreSQL/Redis 数据层，同时让 Java 负责稳定业务控制面，Python 负责快速迭代的 AI、Skill 和 MCP 工具生态。

---

## 4. 技术选型

### 4.1 当前推荐栈

| 层 | 选型 | 理由 |
|----|------|------|
| BFF / API | Spring Boot 3.x + Spring WebMVC | 承接 REST CRUD、认证、权限、事务和前端聚合 API |
| SSE | Spring WebMVC `SseEmitter` | 前端保持 EventSource 接入，Spring 从 Redis Streams 消费 agent 事件后推送 |
| AI 控制面 | Spring AI `ChatClient` + Tool Calling | Java 侧可统一管理模型配置、tool schema、调用观测和审计 |
| MCP 控制面 | Spring AI MCP Client/Server Starters + MCP Annotations | Spring 负责 MCP endpoint 注册、工具发现、健康检查、鉴权和 Java 工具暴露 |
| Agent 编排 | Python FastAPI + LangGraph | 多 Agent 状态图、handoff、上下文压缩和复杂 RAG 更适合 Python 生态 |
| RAG / Vector | Milvus + Spring AI Embedding Gateway + Python RAG Worker | Milvus 存向量；Spring 管 embedding 密钥和权限；Python 做解析、拆分、召回和 prompt 注入 |
| Skill 执行 | Python Skill Runner | 读取 Skill manifest/prompt，组合角色上下文，声明需要的 MCP tools |
| MCP Server | Python FastMCP 优先，Java Spring AI MCP Server 按需 | Python 写文件、测试、浏览器、数据处理类工具；Java 写强依赖 Spring 业务上下文的工具 |
| 数据库 | PostgreSQL 16 | 业务数据、JSONB 配置、pgvector 预留 |
| ORM / Migration | JPA 或 MyBatis Plus + Flyway | Java 侧拥有核心业务表写入权；Python 默认不直接写核心业务表 |
| 缓存 / 队列 | Redis Streams | `agent.jobs`、`agent.events:{threadId}`、`rag.index.jobs`、tool result event、SSE 状态桥接 |
| 存储 | MinIO / S3 | RAG 原始文件、关联目录文件上传、会话导出、备份快照 |
| 密钥 | Spring secrets service + `secret_ref` | Python 只拿临时授权或通过 Spring 内部 API 调用工具，不持久化真实密钥 |

### 4.2 服务职责分工

| 服务 | 负责 | 不负责 |
|------|------|--------|
| Spring Boot BFF | 前端 API、认证、权限、事务、角色/Skill/MCP 配置、SSE 推送、运行日志 | 复杂多 Agent 状态图和具体 Python 工具实现 |
| Spring MCP/Tool Gateway | MCP endpoint registry、工具发现、调用鉴权、限流、审计、健康检查 | 绕过权限直接执行业务工具 |
| Python Agent Service | LangGraph 编排、角色路由、上下文压缩、Skill Runner 调用、LLM 执行 | 直接写核心业务表或直接读取生产密钥 |
| Python RAG Worker | 文档解析、清洗、chunk 拆分、Milvus 写入、查询召回、prompt 上下文注入 | 接收浏览器上传、管理权限或直接暴露 Milvus 给前端 |
| Python/Java MCP Server | 暴露工具、资源和 prompts | 管理全局权限、用户会话和 endpoint 配置 |

### 4.3 选择依据

- 你更熟 Java 和 Python，Spring AI + Python 的维护成本低于 Go 或 Node 主后端。
- Spring Boot 更适合承载认证、权限、数据库事务、配置治理和内部网关。
- Python 生态更适合多 Agent、LangGraph、Skill Runner、RAG、浏览器自动化和数据处理工具。
- MCP 是跨语言协议，MCP Server 可按工具生态选择 Python 或 Java；生产调用统一经过 Spring 治理。
- v1 不引入 Go；Node.js 只作为前端构建和可选工具生态，不作为后端主线。

### 4.4 备选方案处理

- Node.js / NestJS 可作为“前端同语言团队”的备选，不作为当前首选。
- Go + Python 只在高并发网关、大团队服务拆分或明确需要 Go 网络层能力时再评估。
- Serverless 只适合短期 demo；正式产品因为 MCP stdio、长任务、内网工具调用和密钥代理限制，不作为主线。

### 4.5 前端 Store → Spring/Python 模块对照表

| 前端 Store 操作 | 当前实现方式 | 后端对应模块 | 改造要点 |
|---------------|------------|------------|---------|
| threadStore.contexts | THREAD_CONTEXTS + localStorage 合并 | Spring `ThreadController` / `ThreadService` | GET /folders/:id/threads 拉取；client_key 兼容现有 key |
| threadStore.conversations | localStorage 中的 conversations 数组 | Spring `MessageController` / `MessageService` | GET /threads/:id/messages 分页拉取历史 |
| threadStore.currentThreadId | agentDesk.activeThreadId localStorage | Spring `UserPreferenceService` + user_configs 表 | GET /auth/me 或 /me/preferences 返回 active_thread_key |
| threadStore.addRelatedFolder() | webkitdirectory + localStorage | Spring `FolderService` | POST /projects/:id/folders + 自动创建默认 thread |
| threadStore.addThreadForFolder() | localStorage 动态新增 | Spring `ThreadService` | POST /folders/:id/threads + 生成欢迎消息 |
| threadStore.sendMessage() → appendConversationBubble() | push 本地数组 + 模板回复 | Spring `MessageService` + Redis Streams + Python Agent | POST /threads/:id/messages → /internal/agent/jobs → agent.jobs → Python 执行 → Spring SSE 流式返回 |
| `#composer-attach-file` 输入框下方按钮 | 当前 `attachFolder()` 插入“附加目录”文本 | Spring `RagDocumentController` + Python `RagIndexer` | 后端接入后改为打开文件选择并上传索引；索引完成后当前 thread 默认启用 RAG 检索 |
| roleStore.roles | ROLE_LIBRARY + localStorage 覆盖 | Spring `RoleService` | GET /projects/:id/roles；config 存 JSONB |
| roleStore.ensureThreadRole() | 根据 roleStatus 构建 mock 角色 | Spring `RoleOrchestrationService` | 后端遍历 threads → 创建/同步 thread_roles |
| roleStore.updateRole() → 回写 thread | 修改 store 中的 context.label/summary | Spring `RoleService` + `ThreadService` | 若 sourceThreadId 存在，后端事务内联动更新 thread |
| skillStore.toggleSkill() | 本地状态翻转 | Spring `SkillService` | POST /skills/:id/toggle，状态由后端管理 |
| mcpStore.checkEndpoint() | Math.random() 假延迟 | Spring `McpGatewayService` | Spring 连接 MCP endpoint，测量真实延迟并写入 health checks |
| mcpStore.healthItems | HEALTH_ITEMS 常量 | Spring `McpHealthService` | 从 mcp_endpoints 和 mcp_health_checks 动态聚合 |
| 上下文压缩配置 | role config JSON 中的 compression 字段 | Python Agent Service | Python 在构建 LLM 请求时按 compression 策略摘要或截断历史 |

---

## 5. 数据库设计

### 5.1 ER 图核心实体

```
User (用户)
  │
  ├── 1:N ── Project (项目/工作区)
  │            │
  │            ├── 1:N ── Folder (关联目录)
  │            │            │
  │            │            └── 1:N ── Thread (会话)
  │            │                         │
  │            │                         ├── 1:N ── Message (消息)
  │            │                         │
  │            │                         └── M:N ── Role (角色，通过 ThreadRole)
  │            │
  │            ├── 1:N ── Role (项目角色库)
  │            │
  │            ├── 1:N ── Skill (技能模块)
  │            │            └── M:N ── PromptLayer (挂载层)
  │            │
  │            ├── 1:N ── McpEndpoint (MCP端点)
  │            │
  │            └── 1:N ── RagDocument (RAG文档)
  │                         └── 1:N ── RagChunk (文档片段，向量存 Milvus)
  │
  └── 1:1 ── UserConfig (用户配置)
```

### 5.2 前端字段到数据模型映射

| 前端字段 | 后端位置 | 说明 |
|----------|----------|------|
| `context.id` | `threads.client_key` | 兼容 `session-review`、`route-test` 等前端稳定 key；数据库主键仍用 UUID |
| `context.folder` | `folders.name` | 目录 accordion 的分组依据，如 `src/auth`、`src/router`、`tests` |
| `context.file` | `threads.metadata.file` | 当前会话原始文件来源，目录视图仍以 `folder` 为准 |
| `context.label` | `threads.label` | 会话卡片名称，也会同步到会话角色名称 |
| `context.summary` | `threads.summary` | 会话摘要，也会同步到会话角色描述 |
| `context.roles` | `thread_roles` + `threads.metadata.role_keys` | 保留角色编排顺序，正式关系落到关联表 |
| `context.focusRole` | `threads.focus_role_id` + `threads.metadata.focus_role_key` | 当前聚焦角色，后端返回时映射为前端 `focusRole` |
| `context.roleStatus` | `threads.role_status` | `已编排` 或 `未编排`，用于新增会话占位角色 |
| `context.sessionRoleId` | `thread_roles.role_id` + `threads.metadata.session_role_key` | 对应 `ensureThreadRole(context)` 生成的会话角色 |
| `role.*` 配置字段 | `roles` 列 + `roles.config` | 模型、压缩、Prompt、工具、handoff、duties、promptLayers 等放入 JSONB |
| `skill.mounts` | `skills.mounts` | 对应 Prompt 挂载层：系统层、角色层、任务层、记忆层 |
| `endpoint.tools` | `mcp_endpoints.tools` | 从逗号字符串标准化为数组，供 tool registry 使用 |
| `#composer-attach-file` 选择的 `File.name` | `rag_documents.source_name` | 输入框下方文件按钮上传的原始文件名 |
| `File.webkitRelativePath` | `rag_documents.source_path` | 目录上传或多文件上传时保留相对路径；没有则为空 |
| RAG 索引状态 | `rag_documents.status` + `rag_index_jobs.status` | 前端 chip/toast 展示 uploaded/indexing/indexed/failed |

### 5.3 建表 SQL 草案

```sql
-- ========== 用户与项目 ==========

CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email         TEXT UNIQUE NOT NULL,
  name          TEXT NOT NULL,
  avatar_url    TEXT,
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE projects (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name          TEXT NOT NULL DEFAULT '默认工作区',
  description   TEXT,
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE user_configs (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  active_thread_key TEXT,                              -- 替代前端 agentDesk.activeThreadId
  preferences       JSONB DEFAULT '{}',                -- UI 偏好、设置项、备份策略等
  created_at        TIMESTAMPTZ DEFAULT now(),
  updated_at        TIMESTAMPTZ DEFAULT now()
);

-- ========== 目录与会话 ==========

CREATE TABLE folders (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,                          -- 目录名，如 "src/auth"
  sort_order    INT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ DEFAULT now(),
  UNIQUE(project_id, name)
);

CREATE TABLE threads (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  client_key    TEXT NOT NULL,                           -- 前端稳定 key，如 "session-review"
  folder_id     UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
  label         TEXT NOT NULL,                           -- 会话名称，如 "review-agent"
  summary       TEXT,                                    -- 会话摘要
  focus_role_id UUID,                                    -- 当前聚焦角色；FK 在 roles 创建后补
  role_status   TEXT NOT NULL DEFAULT '已编排',           -- '已编排' | '未编排'
  status        TEXT NOT NULL DEFAULT 'active',          -- active | archived
  metadata      JSONB DEFAULT '{}',                      -- file、role_keys、focus_role_key、session_role_key 等扩展字段
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ DEFAULT now(),
  UNIQUE(project_id, client_key)
);

-- threads.metadata 示例：
-- {
--   "file": "src/auth/useSession.ts",
--   "folder": "src/auth",
--   "role_keys": ["primary", "review", "auth"],
--   "focus_role_key": "review",
--   "session_role_key": "thread-role-session-review"
-- }

CREATE TABLE messages (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  thread_id     UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
  client_message_id TEXT,                                -- 前端生成，用于重试幂等
  role          TEXT NOT NULL,                           -- 'user' | 'agent'
  agent_name    TEXT,                                    -- agent 气泡的 title，如 "主助手"
  agent_id      UUID,                                    -- 具体是哪个 agent 角色发出的；FK 在 roles 创建后补
  content       TEXT NOT NULL,                           -- 消息正文
  kind          TEXT DEFAULT 'text',                     -- text | tool_call | tool_result | system
  status        TEXT NOT NULL DEFAULT 'completed',       -- pending | processing | completed | failed | cancelled
  error_code    TEXT,
  completed_at  TIMESTAMPTZ,
  metadata      JSONB DEFAULT '{}',                      -- token 用量、工具调用结果等
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ DEFAULT now(),
  UNIQUE(thread_id, client_message_id)
);

CREATE INDEX idx_messages_thread_id ON messages(thread_id, created_at);
CREATE INDEX idx_messages_project_status ON messages(project_id, status, created_at DESC);

-- ========== 角色 ==========

CREATE TABLE roles (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,                           -- 角色名称 "主助手"
  alias         TEXT,                                    -- 别名 "orchestrator"
  tag           TEXT,                                    -- 标签 "Primary Agent"
  description   TEXT,                                    -- 职责说明
  short_description TEXT,                                -- 简短说明
  status        TEXT NOT NULL DEFAULT 'active',          -- active | archived
  is_builtin    BOOLEAN DEFAULT false,                   -- 是否系统预置角色
  config        JSONB NOT NULL DEFAULT '{}',             -- 角色配置（见下方）
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ DEFAULT now()
);

-- roles.config JSONB 结构：
-- {
--   "model": "GPT-5.4",
--   "provider": "OpenAI",
--   "secret_ref": "secret://project/openai/orchestrator", -- 引用 Spring 密钥管理服务，非明文
--   "endpoint": "https://api.openai.com/v1/responses",
--   "api_format": "OpenAI Responses",
--   "model_mapping": "orchestrator -> gpt-5.4",
--   "config_json": { "reasoning_effort": "medium", "temperature": 0.2 },
--   "compression": "中压缩",
--   "prompt_prefix": "先拆解任务，再调度子 agent...",
--   "routing": "复杂协调交给主助手...",
--   "routing_chips": ["主助手 / GPT-5.4", "跨角色汇总"],
--   "tools": ["读代码", "执行测试", "整理结论"],
--   "handoff": "把其他角色的摘要汇总...",
--   "matrix_copy": "负责拉齐上下文...",
--   "duties": ["协调汇总"],
--   "custom_duty": "需要时协调多角色结论冲突",
--   "prompt_layers": ["系统层", "角色层", "任务层"],
--   "official_url": "https://platform.openai.com/docs",
--   "source_thread_id": "session-review",
--   "source_folder": "src/auth",
--   "icon": "<svg>...</svg>"
-- }

-- 会话与角色关联（多对多）
CREATE TABLE thread_roles (
  thread_id     UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
  role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  is_focus      BOOLEAN DEFAULT false,                   -- 是否为该会话的聚焦角色
  status        TEXT DEFAULT '已编排',                    -- '已编排' | '未编排'
  PRIMARY KEY (thread_id, role_id)
);

ALTER TABLE threads
  ADD CONSTRAINT fk_threads_focus_role FOREIGN KEY (focus_role_id) REFERENCES roles(id);

ALTER TABLE messages
  ADD CONSTRAINT fk_messages_agent FOREIGN KEY (agent_id) REFERENCES roles(id);

-- ========== Skill 能力模块 ==========

CREATE TABLE skills (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,                           -- "frontend-design"
  source        TEXT NOT NULL,                           -- "local skill" | "plugin skill" | "system skill"
  status        TEXT NOT NULL DEFAULT '待启用',           -- '启用' | '停用' | '待启用'
  scope         TEXT,                                    -- 使用范围描述
  mounts        TEXT[] NOT NULL DEFAULT '{}',            -- 挂载层 ["系统层", "任务层"]
  last_run_at   TIMESTAMPTZ,                             -- 最近运行时间；前端展示文案可由后端格式化
  config        JSONB DEFAULT '{}',                      -- 安装来源、装载策略、最近变更说明等扩展配置
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ DEFAULT now()
);

-- ========== MCP 端点 ==========

CREATE TABLE mcp_endpoints (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,                           -- "Figma MCP"
  transport     TEXT NOT NULL,                           -- "stdio" | "http" | "plugin api" | "iab"
  status        TEXT NOT NULL DEFAULT '待连接',           -- '已连接' | '待连接' | '异常'
  auth_type     TEXT NOT NULL,                           -- "OAuth" | "local" | "api_key"
  url           TEXT NOT NULL,                           -- 连接地址
  tools         TEXT[],                                  -- 暴露的工具列表
  latency_ms    INT,                                     -- 延迟
  health_config JSONB DEFAULT '{}',                      -- 健康检查配置、最近错误、工具 schema 摘要
  created_at    TIMESTAMPTZ DEFAULT now(),
  updated_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE mcp_health_checks (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  endpoint_id   UUID NOT NULL REFERENCES mcp_endpoints(id) ON DELETE CASCADE,
  status        TEXT NOT NULL,                           -- '正常' | '异常' | '待确认'
  message       TEXT,
  checked_at    TIMESTAMPTZ DEFAULT now()
);

-- ========== 运行日志 ==========

CREATE TABLE task_logs (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id    UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  thread_id     UUID REFERENCES threads(id),
  type          TEXT NOT NULL,                           -- 'agent_call' | 'tool_use' | 'error' | 'system'
  level         TEXT DEFAULT 'info',                     -- 'debug' | 'info' | 'warn' | 'error'
  message       TEXT NOT NULL,
  metadata      JSONB DEFAULT '{}',
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_task_logs_project ON task_logs(project_id, created_at DESC);

-- ========== RAG 文档索引 ==========

CREATE TABLE rag_documents (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id          UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  thread_id           UUID REFERENCES threads(id) ON DELETE SET NULL,
  folder_id           UUID REFERENCES folders(id) ON DELETE SET NULL,
  uploaded_by         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source_name         TEXT NOT NULL,                         -- 原始文件名
  source_path         TEXT,                                  -- 浏览器可提供的相对路径，如 webkitRelativePath
  storage_uri         TEXT NOT NULL,                         -- MinIO/S3 对象地址
  mime_type           TEXT NOT NULL,
  size_bytes          BIGINT NOT NULL,
  sha256              TEXT NOT NULL,
  status              TEXT NOT NULL DEFAULT 'uploaded',      -- uploaded | indexing | indexed | failed | deleted
  chunk_count         INT NOT NULL DEFAULT 0,
  embedding_model     TEXT,                                  -- 实际使用的 embedding 模型
  embedding_dimension INT,                                   -- Milvus collection 维度
  milvus_collection   TEXT NOT NULL DEFAULT 'agent_desk_chunks',
  metadata            JSONB DEFAULT '{}',                    -- page_count、parser、language、error 等
  error_message       TEXT,
  indexed_at          TIMESTAMPTZ,
  created_at          TIMESTAMPTZ DEFAULT now(),
  updated_at          TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE rag_chunks (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  document_id     UUID NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
  thread_id       UUID REFERENCES threads(id) ON DELETE SET NULL,
  chunk_index     INT NOT NULL,
  content         TEXT NOT NULL,
  token_count     INT,
  page_start      INT,
  page_end        INT,
  content_hash    TEXT NOT NULL,
  vector_id       TEXT NOT NULL,                             -- Milvus 主键或外部 id，用于删除和重建索引
  metadata        JSONB DEFAULT '{}',
  created_at      TIMESTAMPTZ DEFAULT now(),
  UNIQUE(document_id, chunk_index)
);

CREATE TABLE rag_index_jobs (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  document_id     UUID NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
  thread_id       UUID REFERENCES threads(id) ON DELETE SET NULL,
  job_type        TEXT NOT NULL DEFAULT 'index',             -- index | reindex | delete
  status          TEXT NOT NULL DEFAULT 'queued',            -- queued | running | succeeded | failed
  error_message   TEXT,
  metadata        JSONB DEFAULT '{}',
  started_at      TIMESTAMPTZ,
  finished_at     TIMESTAMPTZ,
  created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_rag_documents_project_thread_status ON rag_documents(project_id, thread_id, status);
CREATE INDEX idx_rag_documents_sha256 ON rag_documents(project_id, sha256);
CREATE INDEX idx_rag_chunks_document_index ON rag_chunks(document_id, chunk_index);
CREATE INDEX idx_rag_chunks_project_thread ON rag_chunks(project_id, thread_id);
CREATE INDEX idx_rag_index_jobs_status ON rag_index_jobs(status, created_at);
```


### 5.4 查询模式与附加索引

#### 高频查询索引

```sql
-- 目录排序查询 (GET /folders)
CREATE INDEX idx_folders_project_sort ON folders(project_id, sort_order);

-- 会话按项目/目录 + 状态查询 (GET /folders/:id/threads)
CREATE INDEX idx_threads_project_folder_status ON threads(project_id, folder_id, status);

-- 用户偏好查询 (GET /me/preferences)
CREATE INDEX idx_user_configs_user ON user_configs(user_id);

-- MCP 健康检查按时间查询
CREATE INDEX idx_mcp_health_endpoint_time ON mcp_health_checks(endpoint_id, checked_at DESC);

-- 角色按项目 + 状态查询
CREATE INDEX idx_roles_project_status ON roles(project_id, status);

-- Skill 按项目 + 状态查询
CREATE INDEX idx_skills_project_status ON skills(project_id, status);

-- 任务日志按 thread + 时间查询
CREATE INDEX idx_task_logs_thread_time ON task_logs(thread_id, created_at DESC);

-- 消息游标分页高效查询
CREATE INDEX idx_messages_thread_created ON messages(thread_id, created_at);

-- RAG 文档列表和索引任务查询
-- 见 rag_documents / rag_index_jobs 建表草案中的 idx_rag_* 索引
```

#### 典型查询 SQL

```sql
-- Bootstrap: 项目 + 目录 + 会话 + 最近消息聚合
SELECT f.id, f.name, f.sort_order,
       t.id AS thread_id, t.client_key, t.label, t.summary,
       t.focus_role_id, t.role_status, t.status,
       t.metadata->>'focus_role_key' AS focus_role_key,
       t.metadata->>'role_keys' AS role_keys,
       t.metadata->>'session_role_key' AS session_role_key,
       lm.content AS message_preview,
       lm.created_at AS last_message_at
FROM folders f
JOIN threads t ON t.folder_id = f.id AND t.status = 'active'
LEFT JOIN LATERAL (
  SELECT content, created_at FROM messages
  WHERE thread_id = t.id
  ORDER BY created_at DESC LIMIT 1
) lm ON true
WHERE f.project_id = :projectId
ORDER BY f.sort_order, t.created_at;

-- 游标分页消息
SELECT id, role, agent_name, agent_id, content, kind, metadata, created_at
FROM messages
WHERE thread_id = :threadId
  AND (:cursorCreatedAt IS NULL OR created_at > :cursorCreatedAt)
ORDER BY created_at ASC
LIMIT :limit;

-- 同目录会话角色同步
SELECT t.id, t.client_key, t.label, t.folder_id,
       tr.role_id, tr.is_focus, tr.status AS thread_role_status,
       r.name AS role_name, r.config AS role_config
FROM threads t
LEFT JOIN thread_roles tr ON tr.thread_id = t.id
LEFT JOIN roles r ON r.id = tr.role_id
WHERE t.folder_id = :folderId AND t.status = 'active';

-- 当前会话可用 RAG 文档
SELECT id, source_name, source_path, status, chunk_count, indexed_at, created_at
FROM rag_documents
WHERE project_id = :projectId
  AND thread_id = :threadId
  AND status IN ('uploaded', 'indexing', 'indexed', 'failed')
ORDER BY created_at DESC;
```

### 5.5 数据初始化（Flyway Migration）

第一个 migration 应包含预置角色和会话数据，对应前端 `ROLE_LIBRARY` 和 `THREAD_CONTEXTS`：

```sql
-- V1__schema.sql: 建表
-- V2__seed_data.sql: 预置数据

-- 预置角色
INSERT INTO roles (id, project_id, name, alias, tag, description, short_description, is_builtin, config)
SELECT gen_random_uuid(), :projectId, name, alias, tag, description, short_description, true, config::jsonb
FROM (VALUES
  ('主助手', 'orchestrator', 'Primary Agent', '负责拆解任务...', '总控调度...',
   '{"model":"GPT-5.4","provider":"OpenAI","secret_ref":"secret://project/openai/orchestrator",...}'),
  ('review-agent', 'review-guard', 'Review Agent', '负责变更审查...', '变更审查...',
   '{"model":"GPT-5.4-mini","provider":"OpenAI","secret_ref":"secret://project/openai/review",...}')
  -- ... 其余角色类似
) AS t(name, alias, tag, description, short_description, config);

-- 预置目录和会话
-- ...同理，对应 THREAD_CONTEXTS 中的 7 条预置会话
```

实际 migration 文件中的 `:projectId` 通过 Flyway placeholder 或应用启动后的初始化服务动态替换为当前用户的默认项目 ID。

### 5.6 状态枚举与前端展示映射

后端状态字段必须稳定，因为前端会直接用这些状态驱动 toast、chip、按钮禁用和重试入口。

| 对象 | 字段 | 后端枚举 | 前端展示/行为 |
|------|------|----------|---------------|
| `threads` | `status` | `active`、`archived`、`deleted` | `active` 显示在左侧目录；`archived/deleted` 不进入默认列表 |
| `threads` | `role_status` | `已编排`、`未编排` | `/robot-settings` 中决定是否生成“未编排角色”占位 |
| `messages` | `status` | `pending`、`processing`、`completed`、`failed`、`cancelled` | `pending/processing` 显示 agent 占位气泡；`failed` 显示重试按钮和错误提示 |
| `rag_documents` | `status` | `uploaded`、`indexing`、`indexed`、`failed`、`deleted` | `#composer-context-chip` 显示“正在索引/已索引/索引失败” |
| `rag_index_jobs` | `status` | `queued`、`running`、`succeeded`、`failed` | 上传接口返回后用于轮询或 SSE 状态更新 |
| `skills` | `status` | `启用`、`停用`、`待启用` | Skill 卡片按钮文案在“启用/停用”之间切换 |
| `mcp_endpoints` | `status` | `已连接`、`待连接`、`异常` | MCP 端点卡片和健康摘要展示 |
| `task_logs` | `level` | `debug`、`info`、`warn`、`error` | 系统设置页“运行日志”按级别筛选 |

状态写入原则：

- 浏览器发起写操作后，Spring 先落库成 `pending/queued`，再异步推进到最终状态。
- Python 只通过 Redis Streams 或内部 API 回报状态，不直接更新核心业务表。
- 前端刷新页面后只信任服务端返回状态；`localStorage` 仅作为离线兜底，不覆盖服务端状态。



---

## 6. API 接口设计

### 6.1 接口总览（RESTful）

采用 `/api/v1` 版本前缀，所有需要认证的接口带 `Authorization: Bearer <token>` 头。

```
认证相关
  POST   /api/v1/auth/register              注册
  POST   /api/v1/auth/login                 登录 → 返回 JWT
  POST   /api/v1/auth/refresh               刷新 token
  GET    /api/v1/auth/me                    获取当前用户信息

项目
  GET    /api/v1/projects                   项目列表
  POST   /api/v1/projects                   创建项目
  GET    /api/v1/projects/:id               项目详情
  PATCH  /api/v1/projects/:id               更新项目
  GET    /api/v1/projects/:id/bootstrap     前端初始化聚合数据

用户偏好
  GET    /api/v1/me/preferences              获取当前用户偏好（activeThreadId 等）
  PATCH  /api/v1/me/preferences              更新当前用户偏好

目录
  GET    /api/v1/projects/:id/folders       目录列表（含排序）
  POST   /api/v1/projects/:id/folders       创建关联目录
  DELETE /api/v1/folders/:id                删除目录

会话 (Thread)
  GET    /api/v1/folders/:id/threads        目录下会话列表
  POST   /api/v1/folders/:id/threads        创建新会话
  GET    /api/v1/threads/:id                会话详情
  PATCH  /api/v1/threads/:id                更新会话（名称、摘要等）
  DELETE /api/v1/threads/:id                删除会话
  POST   /api/v1/threads/:id/archive        归档会话

消息 (Message) — 历史查询用
  GET    /api/v1/threads/:id/messages       消息列表（分页，游标翻页）
  POST   /api/v1/threads/:id/messages       发送新消息 → 触发 Agent 编排 → SSE 流式返回

RAG 文档索引
  POST   /api/v1/threads/:id/rag/uploads    输入框下方文件按钮上传文件并创建索引任务
  GET    /api/v1/threads/:id/rag/documents  当前会话 RAG 文档列表
  GET    /api/v1/rag/jobs/:id               查询索引任务状态
  DELETE /api/v1/rag/documents/:id          删除文档并清理 Milvus 向量
  POST   /api/v1/threads/:id/rag/search     调试用召回接口（正式回答走 messages）

角色 (Role)
  GET    /api/v1/projects/:id/roles         项目角色列表
  POST   /api/v1/projects/:id/roles         创建角色
  GET    /api/v1/roles/:id                  角色详情
  PATCH  /api/v1/roles/:id                  更新角色配置
  DELETE /api/v1/roles/:id                  删除角色
  POST   /api/v1/roles/:id/toggle           启停角色

  # 批量编排（前端 ensureThreadRole 的后端对应）
  POST   /api/v1/folders/:id/sync-roles     同步目录下所有会话的角色

Skill
  GET    /api/v1/projects/:id/skills        Skill 列表
  POST   /api/v1/projects/:id/skills        新增 Skill
  PATCH  /api/v1/skills/:id                 更新 Skill 配置
  POST   /api/v1/skills/:id/toggle          启停 Skill
  POST   /api/v1/projects/:id/skills/sync-policy 同步 Prompt 装载策略
  DELETE /api/v1/skills/:id                 删除 Skill

MCP 端点
  GET    /api/v1/projects/:id/mcp           端点列表
  POST   /api/v1/projects/:id/mcp           新增端点
  PATCH  /api/v1/mcp/:id                    更新端点
  DELETE /api/v1/mcp/:id                    删除端点
  POST   /api/v1/mcp/:id/health-check       单端点健康检查
  POST   /api/v1/mcp/health-check-all       批量健康检查
  GET    /api/v1/mcp/health-status          运行健康摘要

任务日志
  GET    /api/v1/projects/:id/logs          日志列表（分页、筛选）

系统设置 / 上下文
  POST   /api/v1/threads/:id/context/compress 触发上下文压缩
  POST   /api/v1/threads/:id/backups          生成当前 thread 备份
  GET    /api/v1/projects/:id/backups         备份快照列表
  GET    /api/v1/projects/:id/tasks           任务队列列表
  GET    /api/v1/projects/:id/tool-permissions 工具授权配置
  PATCH  /api/v1/projects/:id/tool-permissions 更新工具授权配置

流式响应
  GET    /api/v1/threads/:id/stream         建立 SSE 连接，实时接收 Agent 输出
```

### 6.2 内部服务边界

外部前端 API 保持稳定；Spring 与 Python 之间走内部 API 和 Redis Streams，不暴露给浏览器。

```
Spring → Python Agent
  POST /internal/agent/jobs
  payload: thread、message、role config、skill ids、上下文摘要策略
  behavior: Python 接收后写入 Redis Streams agent.jobs，由 worker 消费

Python → Spring Event
  Redis Streams: agent.events:{threadId}
  event: message_start | agent_thinking | text_delta | tool_call | tool_result | agent_handoff | message_complete

Python → Spring Tool Gateway
  POST /internal/tools/invoke
  payload: endpoint_id、tool_name、args、thread_id、agent_id

Spring → Python Skill Runner
  POST /internal/skills/run
  payload: skill_id、role_config、thread_context、task_input

Spring → Python RAG Indexer
  Redis Streams: rag.index.jobs
  payload: document_id、project_id、thread_id、storage_uri、mime_type、chunk_policy
  optional HTTP: POST /internal/rag/index-jobs 仅用于调试、手动重跑或管理后台触发

Python → Spring Embedding Gateway
  POST /internal/embeddings/embed
  payload: texts[]、embedding_model_ref、project_id
  behavior: Spring 使用 Spring AI EmbeddingModel 和 secret_ref 生成向量，Python 不持久化真实 API key

Python RAG Worker → Milvus
  collection: agent_desk_chunks
  behavior: 写入 chunk vector 和 project/thread/document metadata；查询时必须带 project_id/thread_id 过滤
```

固定数据流：

1. 前端发送消息到 Spring `POST /api/v1/threads/:id/messages`。
2. Spring 写入 user message，并调用 Python `/internal/agent/jobs` 提交 job。
3. Python 将 job 写入 Redis Stream `agent.jobs`，Agent Worker 消费后执行 LangGraph/Skill。
4. Python 需要工具时请求 Spring Tool Gateway。
5. Spring 调用 Python/Java MCP Server，并记录审计与日志。
6. Python 写入 `agent.events:{threadId}`，Spring 消费后推送 SSE 并持久化 agent message。

RAG 固定数据流：

1. 用户点击主工作台输入框下方 `#composer-attach-file` 文件按钮，选择文件。
2. 前端上传到 Spring `POST /api/v1/threads/:id/rag/uploads`。
3. Spring 校验权限、文件类型和大小，把原始文件写入 MinIO/S3，创建 `rag_documents` 和 `rag_index_jobs`。
4. Spring 写入 Redis Stream `rag.index.jobs`；Python RagIndexer 消费任务。`/internal/rag/index-jobs` 只作为调试或手动重跑入口。
5. Python 读取文件、解析、拆分 chunk，并通过 Spring `/internal/embeddings/embed` 批量生成 embedding。
6. Python 写入 Milvus 和 `rag_chunks.vector_id`，Spring 更新索引状态。
7. 用户发送问题时，Python Agent 先做 Milvus 相似度检索，再把召回片段作为 `Retrieved Context` 注入最终 prompt。

### 6.3 前端 Store 到 API 的迁移映射

| 前端行为 | 当前 Store 方法 | 后端 API | 返回要求 |
|----------|-----------------|----------|----------|
| 初始化主工作台 | `hydrateFromRoute`、`fileGroups` | `GET /api/v1/projects/:id/bootstrap` 或并行请求 folders/threads/messages/preferences | 返回目录、会话、当前 activeThreadId、每个会话最近消息 |
| 切换线程 | `setActiveThread(threadId)` | `PATCH /api/v1/me/preferences` | 保存 `activeThreadId`，不影响 thread 内容 |
| 新增关联目录 | `addRelatedFolder(selection)` | `POST /api/v1/projects/:id/folders` | 创建 folder，同时创建默认 `primary-agent` 会话和未编排会话角色 |
| 当前目录新增会话 | `addThreadForFolder(folder)` | `POST /api/v1/folders/:id/threads` | 创建独立消息流，`role_status` 默认为 `未编排` |
| 上传索引文件 | `#composer-attach-file` 按钮后续绑定文件选择器 | `POST /api/v1/threads/:id/rag/uploads` | 复用输入框下方按钮；上传成功后返回 document/job 状态，索引完成后当前 thread 默认可 RAG 检索 |
| 发送消息 | `appendConversationBubble(threadId, bubble)` | `POST /api/v1/threads/:id/messages` + `GET /stream` | 先返回入队状态，再通过 SSE 输出 agent 气泡 |
| 编辑会话角色 | `updateRole` + `updateThread` | `PATCH /api/v1/roles/:id` + `PATCH /api/v1/threads/:id` | 当角色绑定 `sourceThreadId` 时，同步更新 thread `label` 和 `summary` |
| Skill 启停和新增 | `toggleSkill`、`addSkill` | `POST /api/v1/skills/:id/toggle`、`POST /api/v1/projects/:id/skills` | 返回标准化 mounts 数组和最近运行展示字段 |
| MCP 检查和新增 | `checkEndpoint`、`checkAll`、`addEndpoint` | `POST /api/v1/mcp/:id/health-check`、`POST /api/v1/mcp/health-check-all`、`POST /api/v1/projects/:id/mcp` | 返回 status、latency_ms、tools_available、checked_at |

### 6.4 v1 API 接入顺序

| 顺序 | 接入内容 | 验收标准 |
|------|----------|----------|
| 1 | 只读初始化：`bootstrap`、folders、threads、roles、skills、mcp | 前端 4 个页面能从后端渲染当前 mock 等价数据 |
| 2 | 用户偏好和会话写入：active thread、关联目录、新增会话、历史消息 | URL thread、用户偏好、目录分组和独立消息流与当前行为一致 |
| 3 | 角色、Skill、MCP 配置保存 | 机器人设置页编辑会同步 thread，能力管理页能保存启停、新增和端点配置 |
| 4 | RAG 上传索引：复用输入框下方文件按钮、MinIO 存储、Milvus 索引、索引状态查询 | 上传文件后能看到 `uploaded/indexing/indexed/failed` 状态，发送消息时可召回当前 thread 文档片段 |
| 5 | Agent 编排和 SSE | 发送消息返回 202，SSE 推送 `text_delta`、`tool_call`、`message_complete` |
| 6 | MCP 真实健康检查 | stdio/Streamable HTTP/SSE/plugin api/iab adapter 经 Spring Gateway 执行并写入检查记录 |

### 6.5 关键接口详解

#### 6.5.1 发送消息（最核心接口）

```
POST /api/v1/threads/:id/messages
Content-Type: application/json
Authorization: Bearer <token>
X-Idempotency-Key: <client_message_id>

Request:
{
  "client_message_id": "msg_01HZY8K9V7W3Q2P6",
  "content": "帮我列一下这三个文件各自可能受影响的逻辑点",
  "context": {
    "active_folder": "src/auth",          // 当前 activeContext.folder
    "attach_folder": true,                // 是否把当前目录作为任务上下文
    "terminal_output": null               // 对应"插入终端输出"按钮插入的内容
  },
  "rag": {
    "enabled": true,                       // 当前 thread 存在 indexed 文档时默认 true
    "top_k": 6,
    "scope": "thread"                      // thread | project；默认 thread，避免跨会话误召回
  }
}

Response: 202 Accepted
{
  "message_id": "uuid",
  "client_message_id": "msg_01HZY8K9V7W3Q2P6",
  "thread_id": "uuid",
  "job_id": "uuid-v7",
  "status": "processing",                 // 消息已入队，流式输出通过 SSE 推送
  "stream_url": "/api/v1/threads/session-review/stream"
}
```

幂等规则：同一个 `thread_id + client_message_id` 重复提交时，Spring 不重复写 user message，不重复发布 agent job，直接返回第一次请求的 `message_id/job_id/status`。前端可在网络超时后安全重试。

#### 6.5.2 SSE 流式响应

```
GET /api/v1/threads/:id/stream
Authorization: Bearer <token>
Last-Event-ID: <optional-last-event-id>

// SSE 事件流：
event: message_start
id: 1714634400000-0
data: {"job_id":"uuid-v7","message_id":"uuid","thread_id":"uuid","client_message_id":"msg_01HZY8K9V7W3Q2P6"}

event: agent_thinking
id: 1714634400000-1
data: {"job_id":"uuid-v7","agent_name":"主助手","agent_id":"uuid","status":"analyzing"}

event: rag_retrieval
id: 1714634400000-2
data: {"job_id":"uuid-v7","matches":[{"source_name":"design-notes.md","score":0.86}],"top_k":6}

event: text_delta
id: 1714634400000-3
data: {"job_id":"uuid-v7","content":"已锁定","seq":1}

event: text_delta
id: 1714634400000-4
data: {"job_id":"uuid-v7","content":"三个关键文件：src/auth/useSession.ts...","seq":2}

event: tool_call
id: 1714634400000-5
data: {"job_id":"uuid-v7","agent_name":"review-agent","tool":"读代码","args":{"file":"src/auth/useSession.ts"},"status":"running"}

event: tool_result
id: 1714634400000-6
data: {"job_id":"uuid-v7","agent_name":"review-agent","tool":"读代码","result":"...","status":"done"}

event: agent_handoff
id: 1714634400000-7
data: {"job_id":"uuid-v7","from":"review-agent","to":"主助手","summary":"发现 3 处变更点"}

event: message_complete
id: 1714634400000-8
data: {"job_id":"uuid-v7","message_id":"uuid","usage":{"prompt_tokens":1200,"completion_tokens":450}}

event: heartbeat
data: {"ts":"2026-05-02T10:00:30Z"}
```

SSE 事件类型对应前端 UI 变化：

| SSE 事件 | 前端 UI 变化 |
|----------|------------|
| `message_start` | 创建或确认 agent 占位气泡，绑定 `message_id/job_id` |
| `agent_thinking` | 对话流中显示 "XXX 正在分析..." |
| `rag_retrieval` | 在上下文 chip 或气泡 metadata 中显示“已召回 N 个片段” |
| `text_delta` | 逐字追加到当前 agent 气泡 |
| `tool_call` | 在气泡中插入工具调用卡片 |
| `tool_result` | 更新工具调用结果 |
| `agent_handoff` | 显示交接标记 |
| `message_complete` | 气泡完成态，更新 token 用量 |
| `heartbeat` | 不改变 UI，仅维持连接和断线检测 |
| `error` | 当前 agent 气泡进入失败态，显示重试入口 |

断点续传规则：Spring 将 Redis Stream entry id 作为 SSE `id`。前端 EventSource 自动重连时会携带 `Last-Event-ID`，Spring 从该 id 之后继续读取 `agent.events:{threadId}`，避免刷新或网络抖动时丢失 `text_delta`。

#### 6.5.3 目录角色同步

```
POST /api/v1/folders/:id/sync-roles
Authorization: Bearer <token>

// 后端逻辑（对应前端 ensureThreadRole）：
// 1. 查出该目录下所有 threads
// 2. 对每条 thread，检查其 thread_roles 关联是否齐全
// 3. 已编排线程 → 确保角色配置完整
// 4. 未编排线程 → 创建 "未编排角色" 占位
// 5. 返回同步后的角色列表

Response 200:
{
  "folder_id": "uuid",
  "thread_roles": [
    {
      "thread_id": "uuid",
      "thread_label": "review-agent",
      "role_id": "uuid",
      "role_name": "review-agent",
      "status": "已编排"
    },
    {
      "thread_id": "uuid",
      "thread_label": "会话 2",
      "role_id": "uuid",
      "role_name": "未编排角色",
      "status": "未编排"
    }
  ]
}
```

#### 6.5.4 MCP 健康检查

```
POST /api/v1/mcp/:id/health-check

// 后端实际执行（与前端 mock 不同）：
// 1. Spring McpGatewayService 根据 transport 类型建立连接
//    - stdio → 本地工具管理器启动进程，Spring 维护连接和超时
//    - streamable-http / sse → Spring AI MCP Client 调用健康接口或 tools/list
//    - plugin api → Spring adapter 调用插件健康接口
// 2. 记录延迟、工具列表、错误信息和状态
// 3. 写入 mcp_health_checks 与 task_logs 表

Response 200:
{
  "id": "uuid",
  "name": "Figma MCP",
  "status": "已连接",
  "latency_ms": 128,
  "tools_available": ["design_context", "use_figma", "generate_diagram"],
  "checked_at": "2026-05-01T10:30:00Z"
}
```


### 6.6 通用响应格式与错误处理

#### 成功响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... },
  "request_id": "uuid-v7"
}
```

#### 分页响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [ ... ],
    "cursor": "eyJjcmVhdGVkX2F0IjoiMjAyNi0wNC0wMVQxMjowMDowMFoifQ==",
    "has_more": true,
    "total": 150
  },
  "request_id": "uuid-v7"
}
```

游标分页用于消息列表（按 `created_at` DESC），普通分页用于 logs 等场景。游标采用 base64url 编码的 JSON `{"created_at": "ISO8601"}`。

#### 错误响应格式

```json
{
  "code": 40001,
  "message": "thread 不存在或无权访问",
  "details": {
    "field": "thread_id",
    "reason": "not_found_or_forbidden"
  },
  "request_id": "uuid-v7"
}
```

#### 错误码规划

| code | HTTP 状态 | 含义 |
|------|----------|------|
| 0 | 200/201 | 成功 |
| 40001 | 404 | 资源不存在或无权访问 |
| 40002 | 400 | 请求参数校验失败 |
| 40003 | 409 | 资源冲突（如重复 client_key） |
| 40004 | 429 | 请求频率超限 |
| 40101 | 401 | 未认证或 token 过期 |
| 40102 | 401 | refresh token 无效 |
| 40301 | 403 | 无项目访问权限 |
| 50001 | 500 | 服务内部错误 |
| 50002 | 502 | Agent 服务不可用 |
| 50003 | 502 | MCP 端点连接失败 |
| 50004 | 504 | Agent 任务超时 |
| 50005 | 502 | RAG 索引服务不可用 |
| 50006 | 502 | Milvus 检索失败 |

### 6.7 完整接口定义

#### 6.7.1 初始化 Bootstrap

```
GET /api/v1/projects/:id/bootstrap
Authorization: Bearer <token>

Response 200:
{
  "code": 0,
  "data": {
    "project": {
      "id": "uuid",
      "name": "默认工作区",
      "description": "..."
    },
    "folders": [
      {
        "id": "uuid",
        "name": "src/auth",
        "sort_order": 0,
        "thread_count": 2
      }
    ],
    "threads": {
      "session-review": {
        "id": "uuid",
        "client_key": "session-review",
        "folder_id": "uuid",
        "label": "review-agent",
        "summary": "正在梳理 session 状态变更点",
        "focus_role_id": "uuid",
        "focus_role_key": "review",
        "role_keys": ["primary", "review", "auth"],
        "role_status": "已编排",
        "session_role_id": "uuid",
        "session_role_key": "thread-role-session-review",
        "status": "active",
        "last_message_at": "2026-05-01T10:30:00Z",
        "message_preview": "优先看 src/auth、src/router 和 tests..."
      }
    },
    "active_thread_key": "session-review",
    "roles": {
      "primary": {
        "id": "uuid",
        "name": "主助手",
        "alias": "orchestrator",
        "tag": "Primary Agent",
        "description": "负责拆解任务...",
        "short_description": "总控调度、拆解问题...",
        "status": "active",
        "is_builtin": true,
        "config": { ... }
      }
    },
    "skills": [ ... ],
    "mcp_endpoints": [ ... ],
    "health_items": [ ... ]
  },
  "request_id": "uuid-v7"
}
```

返回前端四个页面的首屏所需全部只读数据的聚合。后续页面内编辑走单独 API。

#### 6.7.2 用户偏好

```
GET /api/v1/me/preferences
Authorization: Bearer <token>

Response 200:
{
  "code": 0,
  "data": {
    "active_thread_key": "session-review",
    "preferences": {
      "sidebar_collapsed": false,
      "composer_shortcut": "cmd+enter",
      "language": "zh-CN"
    }
  }
}

PATCH /api/v1/me/preferences
Content-Type: application/json

Request:
{
  "active_thread_key": "route-primary",
  "preferences": {                     // 合并写入，传 null 的 key 不更新
    "sidebar_collapsed": true
  }
}

Response 200:
{ "code": 0, "data": { "updated": ["active_thread_key", "preferences.sidebar_collapsed"] } }
```

`active_thread_key` 变更时后端校验 thread 存在且用户可访问；不存在时回退默认值。

#### 6.7.3 目录与会话

```
GET /api/v1/projects/:id/folders?include_threads=true
Authorization: Bearer <token>

Response 200:
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "uuid",
        "name": "src/auth",
        "sort_order": 0,
        "threads": [
          {
            "id": "uuid",
            "client_key": "session-review",
            "label": "review-agent",
            "summary": "正在梳理 session 状态变更点",
            "focus_role_key": "review",
            "role_keys": ["primary", "review", "auth"],
            "role_status": "已编排",
            "session_role_key": "thread-role-session-review",
            "status": "active",
            "last_message_at": "2026-05-01T10:30:00Z"
          }
        ]
      }
    ]
  }
}

POST /api/v1/projects/:id/folders
Content-Type: application/json

Request:
{
  "name": "src/auth",           // 必填，目录显示名
  "sort_order": 0               // 可选，默认 0（排最前）
}

Response 201:
{
  "code": 0,
  "data": {
    "folder": { "id": "uuid", "name": "src/auth", "sort_order": 0 },
    "default_thread": {
      "id": "uuid",
      "client_key": "thread-m2n3k5a7",
      "label": "primary-agent",
      "summary": "新关联目录已加入，等待补充任务目标",
      "focus_role_key": "primary",
      "role_keys": ["primary", "review"],
      "role_status": "未编排",
      "session_role_key": "thread-role-thread-m2n3k5a7",
      "status": "active"
    },
    "initial_messages": [
      {
        "id": "uuid",
        "role": "agent",
        "agent_name": "主助手",
        "content": "已关联 src/auth 目录。你可以直接在输入框里描述要分析、修改或验证的目标。",
        "kind": "text",
        "created_at": "2026-05-02T..."
      }
    ]
  }
}
```

创建目录时自动创建默认会话和欢迎消息，以便前端立即选中。

```
POST /api/v1/folders/:id/threads
Content-Type: application/json

Request:
{
  "label": "会话 2",             // 可选，默认 "会话 {n}"
  "summary": "新的独立会话",     // 可选
  "role_keys": ["primary", "review", "test"],  // 可选
  "focus_role_key": "primary"    // 可选
}

Response 201:
{
  "code": 0,
  "data": {
    "thread": { ... },
    "initial_messages": [ ... ]
  }
}
```

```
PATCH /api/v1/threads/:id
Content-Type: application/json

Request:
{
  "label": "路由守卫专家",       // 可选
  "summary": "关注未登录跳转",   // 可选
  "focus_role_key": "route",     // 可选
  "status": "archived"           // 可选，active | archived
}

Response 200: { "code": 0, "data": { "thread": { ... } } }
```

#### 6.7.4 消息

```
GET /api/v1/threads/:id/messages?cursor=<base64>&limit=50
Authorization: Bearer <token>

Response 200:
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "uuid",
        "client_message_id": "msg_01HZY8K9V7W3Q2P6",
        "role": "user",                  // user | agent
        "agent_name": null,              // agent 气泡的 title
        "agent_id": null,                // 发出消息的角色 uuid
        "content": "我想尽快知道...",
        "kind": "text",                  // text | tool_call | tool_result | system
        "status": "completed",
        "metadata": {},                  // token 用量、工具调用参数等
        "created_at": "2026-05-01T10:00:00Z"
      },
      {
        "id": "uuid",
        "client_message_id": null,
        "role": "agent",
        "agent_name": "主助手",
        "agent_id": "uuid",
        "content": "我已经锁定认证模块、路由守卫...",
        "kind": "text",
        "status": "completed",
        "metadata": {
          "usage": { "prompt_tokens": 800, "completion_tokens": 120 },
          "model": "gpt-5.4"
        },
        "created_at": "2026-05-01T10:00:05Z"
      }
    ],
    "cursor": "eyJjcmVhdGVkX2F0IjoiMjAyNi0wNS0wMVQwOTozMDowMFoifQ==",
    "has_more": true
  }
}
```

消息按 `created_at` ASC 返回（前端对话流是时间正序）。`limit` 默认 50，最大 200。

游标分页说明：前端首次请求不传 `cursor`，后续翻页传上一次响应的 `cursor`。游标内包含当前页最后一条消息的 `created_at`，服务端 `WHERE created_at > cursor.created_at ORDER BY created_at ASC LIMIT :limit`。

#### 6.7.5 RAG 文档索引

输入框下方现有文件按钮 `#composer-attach-file` 是 RAG 上传索引入口。后端接入后，按钮点击不再只插入“附加当前目录”文本，而是打开文件选择器并调用上传接口；索引状态通过 toast、当前会话上下文 chip 或轻量状态条反馈。

```
POST /api/v1/threads/:id/rag/uploads
Authorization: Bearer <token>
Content-Type: multipart/form-data

Form Data:
  files: File[]                         // 支持多文件；目录上传时保留 webkitRelativePath
  scope: thread                         // thread | project，默认 thread
  chunk_policy: default                 // default | code | markdown，可选

Response 202:
{
  "code": 0,
  "data": {
    "documents": [
      {
        "id": "uuid",
        "source_name": "design-notes.md",
        "source_path": "docs/design-notes.md",
        "status": "uploaded",
        "size_bytes": 18240,
        "job_id": "uuid"
      }
    ],
    "thread_id": "uuid",
    "rag_enabled": true
  },
  "request_id": "uuid-v7"
}
```

```
GET /api/v1/threads/:id/rag/documents
Authorization: Bearer <token>

Response 200:
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "uuid",
        "source_name": "design-notes.md",
        "source_path": "docs/design-notes.md",
        "status": "indexed",
        "chunk_count": 18,
        "embedding_model": "text-embedding-3-small",
        "indexed_at": "2026-05-02T10:30:00Z"
      }
    ]
  }
}
```

```
GET /api/v1/rag/jobs/:id
Authorization: Bearer <token>

Response 200:
{
  "code": 0,
  "data": {
    "id": "uuid",
    "document_id": "uuid",
    "status": "running",
    "progress": {
      "stage": "embedding",
      "parsed_pages": 12,
      "chunks_done": 10,
      "chunks_total": 18
    }
  }
}
```

```
POST /api/v1/threads/:id/rag/search
Content-Type: application/json

Request:
{
  "query": "这份设计文档里对权限模型有什么约束？",
  "top_k": 6,
  "scope": "thread"
}

Response 200:
{
  "code": 0,
  "data": {
    "matches": [
      {
        "document_id": "uuid",
        "source_name": "design-notes.md",
        "chunk_id": "uuid",
        "score": 0.86,
        "content": "权限模型要求 owner/editor/viewer 三档...",
        "metadata": { "page": 3, "source_path": "docs/design-notes.md" }
      }
    ]
  }
}
```

正式问答不要求前端单独调用 search。发送消息时，只要当前 thread 有 `indexed` 文档，Spring 下发的 agent job 会带上 `rag.enabled=true`；Python Agent 在生成回答前完成召回和 prompt 注入。

#### 6.7.6 角色 CRUD 与同目录同步

```
GET /api/v1/projects/:id/roles?include_thread_roles=true
Authorization: Bearer <token>

Response 200:
{
  "code": 0,
  "data": {
    "items": [
      {
        "id": "uuid",
        "name": "主助手",
        "alias": "orchestrator",
        "tag": "Primary Agent",
        "description": "负责拆解任务...",
        "short_description": "总控调度...",
        "status": "active",
        "is_builtin": true,
        "config": {
          "model": "GPT-5.4",
          "provider": "OpenAI",
          "secret_ref": "secret://project/openai/orchestrator",
          "endpoint": "https://api.openai.com/v1/responses",
          "api_format": "OpenAI Responses",
          "model_mapping": "orchestrator -> gpt-5.4",
          "config_json": { "reasoning_effort": "medium", "temperature": 0.2 },
          "compression": "中压缩",
          "prompt_prefix": "先拆解任务...",
          "routing": "复杂协调交给主助手...",
          "routing_chips": ["主助手 / GPT-5.4", "跨角色汇总"],
          "tools": ["读代码", "执行测试", "整理结论"],
          "handoff": "把其他角色的摘要汇总...",
          "matrix_copy": "负责拉齐上下文...",
          "duties": ["协调汇总"],
          "custom_duty": "需要时协调多角色结论冲突",
          "prompt_layers": ["系统层", "角色层", "任务层"],
          "official_url": "https://platform.openai.com/docs",
          "source_thread_id": null,
          "source_folder": null,
          "icon": "<svg>...</svg>"
        },
        "bound_threads": []            // 该角色绑定的会话列表
      }
    ]
  }
}

PATCH /api/v1/roles/:id
Content-Type: application/json

Request:
{
  "name": "路由守卫专家",             // 可选
  "alias": "route-expert",            // 可选
  "description": "...",               // 可选
  "short_description": "...",         // 可选
  "config": {                         // 可选，JSONB 合并写入
    "model": "GPT-5.4",
    "compression": "强压缩"
  }
}

Response 200:
{
  "code": 0,
  "data": {
    "role": { ... },
    "synced_threads": [               // 如果该角色有 source_thread_id，这里列出被联动更新的 thread
      {
        "thread_id": "uuid",
        "label_updated": true,
        "summary_updated": true
      }
    ]
  }
}
```

`source_thread_id` 联动规则：当更新角色 `name` 时同步更新对应 thread 的 `label`；更新 `description` 时同步更新 `summary`。联动在 Spring `RoleService.updateRole()` 的事务内完成。

```
POST /api/v1/folders/:id/sync-roles
Authorization: Bearer <token>

// 不传 body，后端自动扫描目录下所有 thread

Response 200:
{
  "code": 0,
  "data": {
    "folder_id": "uuid",
    "folder_name": "src/auth",
    "thread_roles": [
      {
        "thread_id": "uuid",
        "thread_client_key": "session-review",
        "thread_label": "review-agent",
        "role_id": "uuid",
        "role_name": "review-agent",
        "is_focus": true,
        "status": "已编排"
      },
      {
        "thread_id": "uuid",
        "thread_client_key": "thread-abc123",
        "thread_label": "会话 2",
        "role_id": "uuid",
        "role_name": "未编排角色",
        "is_focus": false,
        "status": "未编排"
      }
    ],
    "created": 1,
    "synced": 1
  }
}
```

后端逻辑：遍历 folder 下所有 threads → 已编排的 thread 确保 thread_role 和角色 config 完整 → 未编排的 thread 创建占位角色（name="未编排角色"，model="待编排" 等）→ 返回同步结果。

#### 6.7.7 Skill 管理

```
POST /api/v1/projects/:id/skills
Content-Type: application/json

Request:
{
  "name": "code-reviewer",
  "source": "local skill",
  "scope": "代码审查、风格检查和最佳实践建议",
  "mounts": ["系统层", "角色层"]
}

Response 201:
{
  "code": 0,
  "data": {
    "id": "uuid",
    "name": "code-reviewer",
    "source": "local skill",
    "status": "待启用",
    "scope": "代码审查、风格检查和最佳实践建议",
    "mounts": ["系统层", "角色层"],
    "last_run_at": null,
    "created_at": "2026-05-02T..."
  }
}

POST /api/v1/skills/:id/toggle
// 不传 body，后端翻转 status

Response 200:
{
  "code": 0,
  "data": {
    "id": "uuid",
    "name": "frontend-design",
    "status": "停用",              // 之前是 "启用"
    "toggled_at": "2026-05-02T..."
  }
}
```

#### 6.7.8 MCP 端点

```
POST /api/v1/projects/:id/mcp
Content-Type: application/json

Request:
{
  "name": "Custom REST API",
  "transport": "http",
  "auth_type": "api_key",
  "url": "https://api.example.com/mcp",
  "tools": ["get_data", "post_result"],
  "health_config": {
    "method": "GET",
    "path": "/health",
    "expected_status": 200,
    "timeout_ms": 5000
  }
}

Response 201:
{
  "code": 0,
  "data": {
    "id": "uuid",
    "name": "Custom REST API",
    "transport": "http",
    "status": "待连接",
    "auth_type": "api_key",
    "url": "https://api.example.com/mcp",
    "tools": ["get_data", "post_result"],
    "latency_ms": null,
    "created_at": "2026-05-02T..."
  }
}

POST /api/v1/mcp/health-check-all
Authorization: Bearer <token>
// 不传 body，对项目内所有端点逐一检查

Response 200:
{
  "code": 0,
  "data": {
    "results": [
      {
        "id": "uuid",
        "name": "Figma MCP",
        "status": "已连接",
        "latency_ms": 128,
        "tools_available": ["design_context", "use_figma", "generate_diagram"],
        "checked_at": "2026-05-02T10:00:00Z"
      },
      {
        "id": "uuid",
        "name": "Node REPL",
        "status": "异常",
        "latency_ms": null,
        "tools_available": [],
        "error": "进程启动超时: 5000ms",
        "checked_at": "2026-05-02T10:00:05Z"
      }
    ],
    "summary": {
      "total": 3,
      "connected": 2,
      "failed": 1,
      "checked_at": "2026-05-02T10:00:05Z"
    }
  }
}

GET /api/v1/mcp/health-status
Authorization: Bearer <token>

Response 200:
{
  "code": 0,
  "data": {
    "items": [
      ["Skill 装载", "正常", "3 个 skill 已进入候选池，按页面场景自动启用。"],
      ["MCP 连接", "正常", "当前接口可被页面配置引用，失败时会回到本地摘要。"],
      ["Prompt 挂载", "待确认", "新增 skill 默认不直接进入系统层，需要人工确认范围。"],
      ["密钥状态", "待配置", "页面只展示 secret_ref 或脱敏摘要，真实密钥由 Spring 密钥服务托管。"]
    ]
  }
}
```

### 6.8 Redis Streams 事件协议

Spring 和 Python 之间通过 Redis Streams 传递 Agent 任务和事件。格式固定，两端按相同 schema 序列化。

#### agent.jobs（Spring → Python）

```
Stream Key: agent.jobs
Consumer Group: agent-workers

消息格式:
{
  "job_id": "uuid-v7",
  "thread_id": "uuid",
  "thread_client_key": "session-review",
  "message_id": "uuid",           // 前端 POST 返回的 message uuid
  "content": "帮我分析变更影响",
  "context": {
    "attach_folder": "src/auth",
    "terminal_output": null
  },
  "rag": {
    "enabled": true,
    "scope": "thread",
    "top_k": 6,
    "filters": {
      "project_id": "uuid",
      "thread_id": "uuid",
      "status": "indexed"
    }
  },
  "role_configs": {               // 当前 thread 绑定角色的完整 config 快照
    "primary": { "config": { ... } },
    "review": { "config": { ... } }
  },
  "skill_ids": ["uuid-1"],
  "compression": "中压缩",
  "created_at": "2026-05-02T10:00:00Z"
}
```

#### rag.index.jobs（Spring → Python）

```
Stream Key: rag.index.jobs
Consumer Group: rag-index-workers

消息格式:
{
  "job_id": "uuid-v7",
  "project_id": "uuid",
  "thread_id": "uuid",
  "document_id": "uuid",
  "source_name": "design-notes.md",
  "storage_uri": "s3://agent-desk/rag/uuid/design-notes.md",
  "mime_type": "text/markdown",
  "chunk_policy": {
    "strategy": "default",
    "chunk_tokens": 800,
    "overlap_tokens": 120
  },
  "embedding_model_ref": "secret://project/embedding/default",
  "milvus_collection": "agent_desk_chunks",
  "created_at": "2026-05-02T10:00:00Z"
}
```

#### agent.events:{threadId}（Python → Spring）

```
Stream Key: agent.events:{thread_client_key}   // 如 agent.events:session-review
Consumer Group: sse-pushers

事件类型与格式:

message_start:
{ "event": "message_start", "job_id": "uuid", "message_id": "uuid", "thread_client_key": "session-review" }

agent_thinking:
{ "event": "agent_thinking", "job_id": "uuid", "agent_name": "主助手", "agent_id": "uuid", "status": "analyzing" }

text_delta:
{ "event": "text_delta", "job_id": "uuid", "agent_name": "主助手", "content": "已锁定" , "seq": 1 }

tool_call:
{
  "event": "tool_call",
  "job_id": "uuid",
  "agent_name": "review-agent",
  "tool": "读代码",
  "args": { "file": "src/auth/useSession.ts" },
  "call_id": "call-uuid",
  "status": "running"
}

tool_result:
{
  "event": "tool_result",
  "job_id": "uuid",
  "agent_name": "review-agent",
  "tool": "读代码",
  "call_id": "call-uuid",
  "result": "...",
  "status": "done"
}

agent_handoff:
{
  "event": "agent_handoff",
  "job_id": "uuid",
  "from": "review-agent",
  "to": "主助手",
  "summary": "发现 3 处变更点"
}

rag_retrieval:
{
  "event": "rag_retrieval",
  "job_id": "uuid",
  "matches": [
    { "document_id": "uuid", "source_name": "design-notes.md", "chunk_id": "uuid", "score": 0.86 }
  ],
  "top_k": 6
}

message_complete:
{
  "event": "message_complete",
  "job_id": "uuid",
  "message_id": "uuid",
  "usage": { "prompt_tokens": 1200, "completion_tokens": 450 },
  "model": "gpt-5.4"
}

error:
{
  "event": "error",
  "job_id": "uuid",
  "message": "LLM 调用超时",
  "code": "LLM_TIMEOUT",
  "retryable": true
}
```

#### 消费与持久化流程

```
1. Spring MessageService 写 user message 到 messages 表
2. 如果消息启用 RAG，Spring 在 job 中附带 thread/project/document 过滤条件
3. Spring AgentJobPublisher 写 agent.jobs Stream
4. Python Worker 从 agent.jobs 消费 → 先做 RAG 检索 → LangGraph 执行
5. Python 每产生一个事件 → 写入 agent.events:{threadId}
6. Spring SseStreamService 消费 agent.events:{threadId}:
   a. 通过 SseEmitter 推送给前端 EventSource
   b. message_complete 事件到达时，将完整 agent message 写入 messages 表
   c. tool_call / tool_result 写入 task_logs 表
   d. agent_handoff 和 rag_retrieval 作为 message metadata 或 task_logs 持久化
7. 前端 EventSource 接收到完整事件流，逐字渲染
```



---

## 7. 核心服务设计

### 7.1 Spring Boot BFF / 控制面

Spring Boot 是唯一面向前端的后端入口，负责认证、权限、事务、REST API、SSE、Skill/MCP 配置和工具调用治理。

```
backend-spring/
├── controller/
│   ├── AuthController.java
│   ├── ProjectBootstrapController.java
│   ├── FolderController.java
│   ├── ThreadController.java
│   ├── MessageController.java
│   ├── RoleController.java
│   ├── SkillController.java
│   ├── McpEndpointController.java
│   ├── RagDocumentController.java
│   └── LogController.java
├── service/
│   ├── UserPreferenceService.java
│   ├── ThreadService.java
│   ├── RoleService.java
│   ├── SkillRegistryService.java
│   ├── McpGatewayService.java
│   ├── ToolInvocationService.java
│   ├── RagDocumentService.java
│   ├── RagIndexJobPublisher.java
│   ├── EmbeddingGatewayService.java
│   ├── AgentJobPublisher.java
│   ├── SseStreamService.java
│   └── SecretRefService.java
├── internal/
│   ├── AgentInternalController.java       # /internal/agent/jobs
│   ├── ToolInternalController.java        # /internal/tools/invoke
│   ├── SkillInternalController.java       # /internal/skills/run
│   └── EmbeddingInternalController.java   # /internal/embeddings/embed
├── repository/
│   └── JPA 或 MyBatis Plus mapper
└── migration/
    └── Flyway SQL migrations
```

**BFF 层特点：**
- Controller 只做鉴权、参数校验和响应组装；核心业务在 Service。
- 所有核心业务表由 Spring 写入，Python 不直接写 `threads`、`messages`、`roles`、`skills`、`mcp_endpoints`。
- 发送消息时 Spring 先写 user message，再调用 Python `/internal/agent/jobs`；Python 将任务写入 Redis Streams `agent.jobs`。
- RAG 上传由 Spring 接收浏览器文件并写入 MinIO/S3，Python 只读取后端授予的对象地址或临时访问凭据。
- Spring 从 `agent.events:{threadId}` 消费事件，推送 SSE 并持久化 agent message、tool call 和 task log。
- `SecretRefService` 只解析 `secret_ref`，不向前端或 Python 长期暴露真实密钥。

### 7.2 Python Agent / Skill 执行服务

这是整个系统最核心的后端模块，实现多 Agent 协作。

```
agent-python/
├── app/
│   ├── main.py                  # FastAPI 内部服务入口
│   ├── worker.py                # Redis Streams agent.jobs consumer
│   ├── graph/
│   │   ├── orchestrator.py      # LangGraph 主状态图
│   │   ├── router.py            # 角色路由
│   │   ├── handoff.py           # 子 Agent 交接协议
│   │   └── compressor.py        # 上下文压缩
│   ├── skills/
│   │   ├── runner.py            # Skill Runner
│   │   ├── manifest.py          # skill manifest 解析
│   │   └── prompt_loader.py     # prompt 模板加载
│   ├── rag/
│   │   ├── indexer.py           # 解析、拆分、embedding、写 Milvus
│   │   ├── retriever.py         # query embedding、Milvus 召回、rerank 预留
│   │   ├── loaders.py           # PDF/DOCX/TXT/MD/代码文件 loader
│   │   └── prompt_context.py    # Retrieved Context 注入模板
│   ├── llm/
│   │   ├── client.py            # LLM 统一客户端
│   │   └── providers.py         # OpenAI/Anthropic/Google 等适配
│   └── gateway/
│       └── spring_tools.py      # 调用 Spring /internal/tools/invoke
└── pyproject.toml
```

**多 Agent 协作流程：**

```
用户发送 "帮我分析 src/auth 的变更影响"
  │
  ▼
Spring POST /messages → 写入消息表 → Python /internal/agent/jobs → Redis Streams agent.jobs
  │
  ▼
Python Agent Worker 消费任务：
  │
  1. RAG Retriever 判断当前 thread 是否有 indexed 文档
     ├── 有：embedding 用户问题 → Milvus topK 检索 → 构造 Retrieved Context
     └── 无：跳过 RAG，沿用普通 thread history
  │
  2. 主助手 (primary) 接收任务
     ├── 系统 Prompt：角色 system prompt + promptPrefix
     ├── 上下文：当前 thread 最近 N 条消息 + 附加目录信息 + Retrieved Context
     └── 决策：需要 review-agent 分析变更 + test-agent 找测试缺口
  │
  3. 主助手 → 生成子任务 → 投递到不同 Agent
     ├── review-agent：读 src/auth/*.ts，筛选变更点，评估风险
     └── test-agent：检查 tests/auth-*.spec.ts，列出断言缺口
  │
  4. 子 Agent 并行执行
     ├── review-agent → Spring Tool Gateway → MCP read-file → 返回变更摘要
     └── test-agent → Spring Tool Gateway → MCP test-tool → 返回缺口列表
  │
  5. 主助手汇总
     ├── 汇总 review-agent + test-agent 的结论
     ├── 按优先级排序
     └── 生成最终输出
  │
  6. SSE 推送事件流到前端
     Python 写 agent.events:{threadId} → Spring SseEmitter → 前端 EventSource
```

### 7.3 Skill 服务写法

Skill 默认不是 MCP，而是 “Prompt / 策略 / 工具组合包”。

| 层 | 职责 | 技术实现 |
|----|------|----------|
| Skill Registry | 管理 name、source、status、scope、mounts、version、config、last_run | Spring `SkillRegistryService` + PostgreSQL |
| Skill Runner | 加载 manifest、prompt 模板、角色配置和 thread 上下文 | Python `skills/runner.py` |
| Tool Binding | Skill 声明需要的 MCP tools，由 Spring 做工具发现和调用治理 | Spring `ToolInvocationService` |
| 升级为 MCP | 只有跨客户端复用的执行能力才升级成 MCP Server | Python FastMCP 或 Java Spring AI MCP Server |

### 7.4 上下文压缩服务

对应前端 SystemSettings 中的压缩强度配置：

- **关闭压缩**：保留完整对话历史
- **轻压缩**：对超过 4K token 的对话做摘要压缩
- **中压缩**：对超过 2K token 的对话做摘要 + 关键信息提取
- **强压缩**：仅保留最近 3 轮对话 + 结构化摘要

压缩策略作为角色配置的一部分，由 Spring 下发给 Python Agent，Python 在 LangGraph 执行前读取并应用。



### 7.5 Spring Boot 配置参考

#### application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: agent-desk-bff
  
  datasource:
    url: jdbc:postgresql://localhost:5432/agentdesk
    username: ${DB_USERNAME:agentdesk}
    password: ${DB_PASSWORD:change-me}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  
  flyway:
    enabled: true
    locations: classpath:db/migration
  
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      streams:
        agent-jobs: agent.jobs
        agent-events-prefix: agent.events
        rag-index-jobs: rag.index.jobs
  
  ai:
    vectorstore:
      milvus:
        client:
          host: ${MILVUS_HOST:localhost}
          port: ${MILVUS_PORT:19530}
        database-name: default
        collection-name: agent_desk_chunks
        embedding-dimension: ${EMBEDDING_DIMENSION:1536}
        index-type: IVF_FLAT
        metric-type: COSINE
    mcp:
      client:
        enabled: true
        request-timeout: 30s
      server:
        enabled: false           # 按需在 mcp-tools-python 或 mcp-tools-java 模块开启
  
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER:http://localhost:8080}

# 内部服务地址
app:
  internal:
    agent-service-url: ${AGENT_SERVICE_URL:http://agent-python:8000}
    agent-job-timeout: 300s
    sse:
      heartbeat-interval: 30s
      reconnect-timeout: 60s
  secrets:
    backend: ${SECRETS_BACKEND:vault}    # vault | env | file
  storage:
    endpoint: ${STORAGE_ENDPOINT:http://minio:9000}
    bucket: agent-desk
    access-key: ${STORAGE_ACCESS_KEY:change-me}
    secret-key: ${STORAGE_SECRET_KEY:change-me}
  rag:
    upload-button-id: composer-attach-file
    max-file-size: 50MB
    allowed-mime-types:
      - text/plain
      - text/markdown
      - application/pdf
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      - text/csv
    chunk-tokens: 800
    overlap-tokens: 120
    default-top-k: 6

logging:
  level:
    com.agentdesk: DEBUG
    org.springframework.ai: DEBUG
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg - requestId=%X{requestId}%n"
```

#### 关键 Bean 注册

```java
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AgentDeskConfig {

    // Redis Streams 消息序列化
    @Bean
    public RedisTemplate<String, AgentJob> agentJobRedisTemplate(
            RedisConnectionFactory factory) {
        RedisTemplate<String, AgentJob> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(AgentJob.class));
        return template;
    }

    // SSE 连接池
    @Bean
    public SseEmitterRegistry sseEmitterRegistry() {
        return new SseEmitterRegistry();
    }

    // MCP Client 工厂
    @Bean
    public McpClientFactory mcpClientFactory(
            McpEndpointRepository endpointRepo,
            SecretRefService secretRefService) {
        return new McpClientFactory(endpointRepo, secretRefService);
    }

    // 内部 API 客户端（Spring → Python）
    @Bean
    public AgentInternalClient agentInternalClient(
            @Value("${app.internal.agent-service-url}") String baseUrl,
            RestClient.Builder builder) {
        return new AgentInternalClient(builder.baseUrl(baseUrl).build());
    }
}
```

### 7.6 Python 服务配置参考

#### pyproject.toml

```toml
[project]
name = "agent-python"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
    "fastapi>=0.115.0",
    "uvicorn[standard]>=0.32.0",
    "redis[hiredis]>=5.0",
    "langgraph>=0.2.0",
    "langchain>=0.3.0",
    "langchain-openai>=0.2.0",
    "langchain-anthropic>=0.2.0",
    "openai>=1.60.0",
    "pydantic>=2.10.0",
    "httpx>=0.28.0",
    "pymilvus>=2.5.0",
    "pypdf>=5.0.0",
    "python-docx>=1.1.0",
    "tiktoken>=0.8.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "pytest-asyncio>=0.24.0",
    "redis-om>=0.3.0",
]
```

#### Agent Worker 入口

```python
# worker.py
import asyncio
import json
from redis.asyncio import Redis
from app.graph.orchestrator import AgentOrchestrator

STREAM_KEY = "agent.jobs"
GROUP = "agent-workers"
CONSUMER = "worker-1"

async def main():
    redis = Redis.from_url("redis://localhost:6379", decode_responses=True)
    
    # 创建 Consumer Group
    try:
        await redis.xgroup_create(STREAM_KEY, GROUP, id="0", mkstream=True)
    except Exception:
        pass  # group 已存在
    
    orchestrator = AgentOrchestrator(redis)
    
    while True:
        # 读取未确认消息（pending）+ 新消息
        messages = await redis.xreadgroup(
            GROUP, CONSUMER, {STREAM_KEY: ">"}, count=1, block=5000
        )
        for stream, entries in messages:
            for entry_id, fields in entries:
                job = json.loads(fields["data"])
                try:
                    await orchestrator.run(job)
                    await redis.xack(STREAM_KEY, GROUP, entry_id)
                except Exception as e:
                    # 记录错误事件
                    await redis.xadd(
                        f"agent.events:{job['thread_client_key']}",
                        {"data": json.dumps({
                            "event": "error",
                            "job_id": job["job_id"],
                            "message": str(e),
                            "retryable": True
                        })},
                        maxlen=1000
                    )

if __name__ == "__main__":
    asyncio.run(main())
```

### 7.7 密钥管理

```
Spring SecretRefService 的 lookup 流程：

前端/数据库存储: "secret_ref": "secret://project/openai/orchestrator"
                            │
                            ▼
Spring SecretRefService.resolve("secret://project/openai/orchestrator")
                            │
                  ┌─────────┴─────────┐
                  ▼                   ▼
          Vault / K8s Secret    env / file backend
          (生产)               (开发)
                  │                   │
                  └─────────┬─────────┘
                            ▼
             返回临时凭据（仅内存，不入日志、不入 API 响应）
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
    Spring 调用 LLM 时注入        Python 通过 Spring 内部 API
    Authorization header          获取临时 token，用完即弃
```

Python 不持久化密钥。每次 Agent 任务开始时通过 Spring `/internal/agent/credentials?job_id=xxx` 获取当次任务所需的临时凭据，任务结束后凭据失效。

### 7.8 RAG 检索与索引服务

RAG 是当前 Spring AI + Python 架构中的独立能力，不作为 MCP 或 Skill。它的产品入口固定为主工作台输入框下方现有文件按钮 `#composer-attach-file`：用户点击后选择文件，后端完成上传、索引和后续检索。

#### 7.8.1 前端入口约束

| 前端位置 | 当前行为 | 后端接入后行为 |
|----------|----------|----------------|
| `#composer-attach-file` | `attachFolder()` 插入“附加目录：当前目录”文本 | 打开文件选择器，上传到 `/api/v1/threads/:id/rag/uploads`，创建当前 thread 的 RAG 索引 |
| `#composer-context-chip` | 展示“已附加当前目录”等状态 | 展示“正在索引 N 个文件 / 已索引 N 个片段 / 索引失败”等轻量状态 |
| `sendMessage()` | 只提交文本和 context | 当前 thread 有 indexed 文档时默认携带 `rag.enabled=true`，由后端控制是否召回 |

侧边栏的“新增关联目录”仍用于创建 folder/thread，不承担 RAG 上传索引。这样可以避免“项目目录管理”和“当前问题资料上传”两个概念混在一起。

#### 7.8.2 索引流水线

```
Browser file input
  → Spring RagDocumentController
  → MinIO/S3 保存原始文件
  → PostgreSQL 写 rag_documents / rag_index_jobs
  → Redis Streams rag.index.jobs
  → Python RagIndexer
  → Spring EmbeddingGatewayService
  → Milvus agent_desk_chunks collection
  → PostgreSQL 回写 rag_chunks.vector_id 和 document status
```

| 步骤 | 归属 | 说明 |
|------|------|------|
| 文件接收 | Spring | 校验用户对 thread/project 的权限，限制大小和 MIME 类型 |
| 原文保存 | Spring + MinIO/S3 | 原始文件是事实来源，Milvus 只是可重建索引 |
| 解析拆分 | Python | PDF、DOCX、Markdown、TXT、CSV、代码文件走不同 loader |
| Embedding | Spring 优先 | v1 通过 `/internal/embeddings/embed` 使用 Spring AI `EmbeddingModel`，避免 Python 长期持有 API key |
| 向量写入 | Python + Milvus | 写入 vector、chunk content 和 metadata；`project_id/thread_id/document_id` 必须可过滤 |
| 状态回写 | Spring | 更新 `rag_documents.status`、`chunk_count`、`indexed_at` 和失败原因 |

默认 chunk 策略：`chunk_tokens=800`、`overlap_tokens=120`。代码文件可以按函数、类、导出块优先切分；Markdown 优先按标题层级切分；PDF/DOCX 需要保留页码，便于回答时引用来源。

#### 7.8.3 Milvus Collection 设计

Milvus collection 建议命名为 `agent_desk_chunks`，向量维度必须与 embedding 模型保持一致。v1 可使用 Spring AI Milvus VectorStore 的默认配置思路，开发环境连接本地 Milvus Standalone。

| 字段 | 类型 | 说明 |
|------|------|------|
| `vector_id` | varchar / primary key | 与 `rag_chunks.vector_id` 对应 |
| `embedding` | float vector | 文档片段向量 |
| `content` | varchar / json | chunk 文本，可按 Milvus 字段限制决定是否只存摘要 |
| `project_id` | varchar | 必须过滤 |
| `thread_id` | varchar | 默认过滤范围 |
| `document_id` | varchar | 删除或重建索引用 |
| `source_name` | varchar | 回答引用来源 |
| `source_path` | varchar | webkitRelativePath 或上传路径 |
| `page_start/page_end` | int | PDF/DOCX 引用 |
| `content_hash` | varchar | 去重和重建索引 |

索引类型建议 v1 使用 `HNSW + COSINE`；如果安装的 Milvus/Spring AI 版本以 `IVF_FLAT + COSINE` 更易配置，则先用 `IVF_FLAT`，再在数据量上来后切换 HNSW。无论选择哪种索引，查询必须带 `project_id` 和默认 `thread_id` 过滤。

#### 7.8.4 查询与 Prompt 注入

用户发送消息时不需要手动点“搜索”。Python Agent 的第一步是 RAG Retriever：

1. 判断当前 thread 是否存在 `rag_documents.status='indexed'`。
2. 通过 Spring Embedding Gateway 对用户 query 做 embedding。
3. 使用 Milvus 按 `project_id + thread_id + status=indexed` 过滤并召回 topK。
4. 可选 rerank；v1 可以先不引入 reranker。
5. 将片段组装成 `Retrieved Context`，放入角色 prompt 的任务层。
6. 回答中保留来源引用，如 `design-notes.md:page 3`。

Prompt 注入格式建议：

```text
Retrieved Context:
[1] source=design-notes.md page=3 score=0.86
权限模型要求 owner/editor/viewer 三档...

[2] source=api-contract.md section=Upload score=0.81
上传接口必须返回 job_id 和 document_id...

请优先基于 Retrieved Context 回答；如果上下文不足，明确说明缺口，不要编造来源。
```

#### 7.8.5 Skill 与 MCP 的边界

- RAG 文档索引不是 Skill：它是系统基础能力，跟随 thread/project 权限运行。
- RAG 检索默认也不是 MCP：它由 Python Agent 内部调用 Milvus 完成，避免每次回答绕 MCP 协议增加延迟。
- 如果后续要把“知识库检索”暴露给外部客户端，可再封装成 MCP Server，由 Spring MCP Gateway 注册和审计。
- Skill 可以声明“需要 RAG 上下文”，但 Skill Runner 只消费召回片段，不直接上传文件或写 Milvus。



### 7.9 Spring ↔ Python 内部 API 契约

Spring 和 Python 之间通过 Redis Streams 传递任务，但以下场景需要同步 REST 调用。所有 `/internal/**` 端点仅允许 `backend-spring` 和 `agent-python` 服务间调用，对外部不可达。

#### 7.9.1 内部 API 总览

| 端点 | 方向 | 用途 |
|------|------|------|
| `POST /internal/agent/jobs` | Spring → Python | 提交 Agent 编排任务（同步确认入口，实际任务走 Redis Streams） |
| `GET /internal/agent/jobs/:id` | Spring → Python | 查询任务状态 |
| `POST /internal/tools/invoke` | Python → Spring | Agent 通过 Spring Gateway 调用 MCP 工具 |
| `POST /internal/skills/run` | Spring → Python | 触发 Skill Runner 执行指定 skill |
| `POST /internal/embeddings/embed` | Python → Spring | 获取文本向量（Spring 持有 embedding API key） |
| `GET /internal/health/workers` | Spring → Python | Spring 查询 Python worker 状态 |
| `POST /internal/rag/reindex` | Spring → Python | 触发 RAG 文档重新索引 |

#### 7.9.2 POST /internal/agent/jobs

Spring 在用户发送消息后调用，Python 同步确认任务已入队 Redis Streams。

```
POST /internal/agent/jobs
Authorization: Bearer <internal-shared-secret>
Content-Type: application/json

Request:
{
  "job_id": "uuid-v7",
  "thread_client_key": "session-review",
  "thread_id": "uuid",
  "project_id": "uuid",
  "user_id": "uuid",
  "content": "帮我分析 src/auth 的变更影响",
  "context": {
    "attach_folder": "src/auth",
    "terminal_output": null
  },
  "rag": {
    "enabled": true,
    "top_k": 6,
    "scope": "thread"
  },
  "role_configs": {
    "primary": {
      "id": "uuid",
      "name": "主助手",
      "config": {
        "model": "GPT-5.4",
        "provider": "OpenAI",
        "endpoint": "https://api.openai.com/v1/responses",
        "api_format": "OpenAI Responses",
        "secret_ref": "secret://project/openai/orchestrator",
        "config_json": { "reasoning_effort": "medium", "temperature": 0.2 },
        "compression": "中压缩",
        "prompt_prefix": "先拆解任务，再调度子 agent...",
        "routing": "复杂协调交给主助手...",
        "tools": ["读代码", "执行测试", "整理结论"],
        "handoff": "把其他角色的摘要汇总...",
        "prompt_layers": ["系统层", "角色层", "任务层"]
      }
    },
    "review": {
      "id": "uuid",
      "name": "review-agent",
      "config": { ... }
    }
  },
  "thread_history": [
    {
      "role": "user",
      "content": "之前的问题...",
      "created_at": "2026-05-01T10:00:00Z"
    },
    {
      "role": "agent",
      "agent_name": "主助手",
      "content": "之前的回答...",
      "created_at": "2026-05-01T10:00:05Z"
    }
  ],
  "preferences": {
    "language": "zh-CN"
  }
}

Response 202:
{
  "code": 0,
  "data": {
    "job_id": "uuid-v7",
    "status": "queued",
    "stream_key": "agent.events:session-review",
    "queued_at": "2026-05-02T10:00:00Z"
  }
}

Response 503 (Python 不可用):
{
  "code": 50002,
  "message": "Agent 服务不可用",
  "details": { "reason": "redis_connection_failed" }
}
```

调用约定：
- Spring 负责在调用前将 user message 写入 `messages` 表（status=pending）。
- Python 入队 Redis Streams `agent.jobs` 后返回 202。
- Spring 收到 202 后立即返回前端 202 + message_id，并开始消费 `agent.events:{threadId}`。
- 如果返回 503，Spring 将 message status 更新为 `failed`，前端展示 "Agent 暂时不可用"。
- `role_configs` 是调用时刻的快照；Python 不自行查询数据库获取角色配置。
- `thread_history` 默认传递最近 20 轮（40 条消息），超过则由 Spring 截断。

#### 7.9.3 POST /internal/tools/invoke

Python Agent 在执行过程中需要调用 MCP 工具时，不直接访问 MCP Server，而是通过 Spring Tool Gateway 统一鉴权和审计。

```
POST /internal/tools/invoke
Authorization: Bearer <internal-shared-secret>
Content-Type: application/json

Request:
{
  "call_id": "uuid-v7",
  "job_id": "uuid-v7",
  "thread_id": "uuid",
  "agent_id": "uuid",
  "agent_name": "review-agent",
  "tool_name": "读代码",
  "tool_args": {
    "file": "src/auth/useSession.ts",
    "start_line": 1,
    "end_line": 100
  },
  "mcp_endpoint_id": "uuid",
  "timeout_ms": 30000
}

Response 200:
{
  "code": 0,
  "data": {
    "call_id": "uuid-v7",
    "status": "completed",
    "result": {
      "content": "export function useSession() { ... }",
      "lines": 100,
      "language": "typescript"
    },
    "latency_ms": 45,
    "token_usage": null
  }
}

Response 408 (超时):
{
  "code": 50004,
  "message": "工具调用超时",
  "data": {
    "call_id": "uuid-v7",
    "status": "timeout",
    "timeout_ms": 30000
  }
}

Response 403 (工具未授权):
{
  "code": 40301,
  "message": "工具未授权或不存在",
  "details": {
    "tool_name": "读代码",
    "agent_id": "uuid",
    "reason": "agent_not_authorized_for_tool"
  }
}
```

调用链：Python Agent → `POST /internal/tools/invoke` → Spring `ToolInvocationService` → 根据 endpoint transport 选择调用方式（stdio 进程、HTTP、plugin adapter）→ 写 `task_logs` → 返回结果给 Python。

#### 7.9.4 POST /internal/embeddings/embed

Python RAG 模块需要文本向量时，通过 Spring 的 Embedding Gateway 获取，避免 Python 持有 embedding API key。

```
POST /internal/embeddings/embed
Authorization: Bearer <internal-shared-secret>
Content-Type: application/json

Request:
{
  "texts": [
    "权限模型要求 owner/editor/viewer 三档...",
    "上传接口必须返回 job_id 和 document_id..."
  ],
  "model": "text-embedding-3-small",
  "dimensions": 1536
}

Response 200:
{
  "code": 0,
  "data": {
    "embeddings": [
      { "index": 0, "embedding": [0.0012, -0.0034, ...] },
      { "index": 1, "embedding": [0.0021, -0.0018, ...] }
    ],
    "model": "text-embedding-3-small",
    "usage": { "total_tokens": 45 }
  }
}
```

调用约定：
- 单次最多 100 条文本，每条不超过 8191 tokens。
- Python 负责 chunk 拆分，Spring 只做 embedding 转发。
- embedding API key 通过 `secret_ref` 解析，不返回给 Python。

#### 7.9.5 POST /internal/skills/run

```
POST /internal/skills/run
Authorization: Bearer <internal-shared-secret>
Content-Type: application/json

Request:
{
  "skill_id": "uuid",
  "skill_name": "frontend-design",
  "job_id": "uuid-v7",
  "thread_id": "uuid",
  "context": {
    "thread_history": [ ... ],
    "role_config": { ... },
    "rag_context": "Retrieved Context: ..."
  },
  "mounts": ["系统层", "任务层"]
}

Response 200:
{
  "code": 0,
  "data": {
    "skill_id": "uuid",
    "status": "completed",
    "output": "基于 frontend-design skill 的分析结果...",
    "prompt_used": "系统层 + 任务层 prompt 模板",
    "latency_ms": 2300
  }
}
```

#### 7.9.6 内部服务鉴权

所有 `/internal/**` 端点使用 shared secret 鉴权：

```
Spring 端配置:
agent-python.internal.secret=${INTERNAL_SECRET:internal-dev-secret-change-me}

Python 端配置:
INTERNAL_SECRET=internal-dev-secret-change-me
INTERNAL_SPRING_URL=http://backend-spring:8080
```

- 开发环境使用固定 shared secret。
- 生产环境通过 K8s Secret 注入，或升级为 mTLS。
- 请求头：`Authorization: Bearer <internal-shared-secret>`。

#### 7.9.7 内部调用超时与重试

| 端点 | 连接超时 | 读取超时 | 重试策略 |
|------|---------|---------|---------|
| `/internal/agent/jobs` | 2s | 10s | 不重试，Spring 返回 503 给前端 |
| `/internal/tools/invoke` | 2s | 由 `timeout_ms` 控制（最大 120s） | tool 级别最多重试 1 次（仅网络错误） |
| `/internal/embeddings/embed` | 2s | 30s | 重试 2 次，间隔 1s/2s |
| `/internal/skills/run` | 2s | 120s | 不重试，超时返回 skill 执行失败 |
| `/internal/health/workers` | 1s | 3s | 不重试 |


---

## 8. 实时通信方案

### 8.1 推荐：SSE (Server-Sent Events)

理由：
- 前端消息流是单向的（服务端 → 前端推送 Agent 输出），SSE 天然匹配
- Spring WebMVC `SseEmitter` 可以保持前端 EventSource 接入方式不变
- Python 不直接连接前端，所有 agent event 通过 Redis Streams 交给 Spring 推送
- 比 WebSocket 简单：HTTP 协议原生支持，不需要额外协议升级
- 自动重连：浏览器 EventSource API 内置断线重连
- 与 HTTP/2 多路复用兼容

### 8.2 连接管理

```typescript
// 前端 Store 中替换 mock 的 appendConversationBubble：

// useConversation.js (新增 composable)
export function useConversation(threadId) {
  const messages = ref([]);
  let eventSource = null;

  function connect() {
    eventSource = new EventSource(`/api/v1/threads/${threadId}/stream`, {
      // 通过 Cookie 或 Authorization header 认证
    });

    eventSource.addEventListener('text_delta', (e) => {
      const { content } = JSON.parse(e.data);
      // 追加到当前 agent 气泡
      appendToCurrentBubble(content);
    });

    eventSource.addEventListener('tool_call', (e) => {
      // 插入工具调用卡片
      insertToolCallCard(JSON.parse(e.data));
    });

    eventSource.addEventListener('message_complete', (e) => {
      const { usage } = JSON.parse(e.data);
      finishCurrentBubble(usage);
    });

    eventSource.onerror = () => {
      // 自动重连，或提示用户
    };
  }

  function disconnect() {
    eventSource?.close();
  }

  return { messages, connect, disconnect };
}
```

### 8.3 可选：内部长连接

浏览器侧不直接连 MCP。若某些 MCP transport 或浏览器自动化工具需要长连接，优先在 Spring Gateway 与 MCP Server / Browser Worker 之间处理：

```
Vue Client ←→ SSE ←→ Spring Gateway ←→ WebSocket/stdio/HTTP ←→ MCP 工具进程
```

---

## 9. 多 Agent 编排引擎

### 9.1 系统架构

```
                    ┌─────────────────────┐
                    │ Python LangGraph    │
                    │ Orchestrator        │
                    │                     │
                    │  - 任务拆分          │
                    │  - 角色路由          │
                    │  - Skill 装载        │
                    │  - 结果汇总          │
                    └──────┬──────────────┘
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐
      │ review   │  │ test     │  │ route    │
      │ agent    │  │ agent    │  │ agent    │
      │          │  │          │  │          │
      │ LLM +    │  │ LLM +    │  │ LLM +    │
      │ MCP tools│  │ MCP tools│  │ MCP tools│
      └──────────┘  └──────────┘  └──────────┘
```

### 9.2 角色路由规则

由 Python Router 节点根据任务内容、角色配置和 Skill mounts 决定由哪个 Agent 处理：

```
if 任务涉及 "diff" | "变更" | "影响面" | "回归"      → review-agent
if 任务涉及 "测试" | "用例" | "断言" | "smoke"       → test-agent
if 任务涉及 "路由" | "守卫" | "跳转" | "白名单"      → route-agent
if 任务涉及 "截图" | "视觉" | "页面差异"             → snapshot-agent
if 任务涉及 "token" | "session" | "认证"            → auth-agent
else                                                 → 主助手 (primary)
```

规则存储在 `roles.config.routing` 字段中，由 Spring 管理配置，Python Agent 执行时读取快照。

### 9.3 Agent 交接协议

```
子Agent 完成分析 → 输出结构化摘要
  {
    "agent": "review-agent",
    "status": "done",
    "summary": "src/auth/useSession.ts 有 3 处变更点...",
    "risks": ["token 刷新逻辑变更可能影响自动登录"],
    "confidence": 0.85,
    "evidence": ["L42: refreshToken 超时判断改为 <= 而非 <"]
  }
  ↓
  LangGraph Orchestrator 收集所有子 Agent 摘要
  ↓
  主助手合成最终输出，按优先级排序
  ↓
  Python 写入 Redis Streams，Spring SSE 推送 agent_handoff + text_delta + message_complete
```

### 9.4 错误恢复与重试机制

#### 9.4.1 任务级重试

Python Agent Worker 消费 `agent.jobs` 时可能遇到三类错误，处理策略不同：

| 错误类型 | 示例 | 重试策略 | 最大重试 | 退避 |
|---------|------|---------|---------|------|
| 瞬时错误 | Redis 读取超时、Milvus 短暂不可用 | 自动重试 | 3 次 | 指数退避 2s/4s/8s |
| LLM 调用错误 | API 限流 429、服务端 5xx | 自动重试 | 3 次 | 指数退避 5s/10s/20s，遇 429 使用 Retry-After 头 |
| 持久错误 | 角色配置非法、thread 不存在 | 不重试 | 0 | 直接写入死信并通知 Spring |
| 超时 | Agent 总执行时间超过 300s | 不重试 | 0 | 发送 `message_error` 事件，标记消息 failed |

```python
# agent-python/app/worker.py 重试逻辑示意
import asyncio
from app.errors import TransientError, PermanentError

MAX_RETRIES = 3
BASE_DELAY = 2.0

async def process_job_with_retry(job: dict, orchestrator) -> None:
    for attempt in range(MAX_RETRIES + 1):
        try:
            await orchestrator.run(job)
            return
        except PermanentError as e:
            await publish_dead_letter(job, str(e))
            await publish_event(job["thread_client_key"], {
                "event": "message_error",
                "error": str(e),
                "recoverable": False
            })
            return
        except TransientError as e:
            if attempt < MAX_RETRIES:
                delay = BASE_DELAY * (2 ** attempt)
                await asyncio.sleep(delay)
                continue
            # 重试耗尽
            await publish_dead_letter(job, str(e))
            await publish_event(job["thread_client_key"], {
                "event": "message_error",
                "error": f"重试 {MAX_RETRIES} 次后仍然失败: {e}",
                "recoverable": True,
                "retry_after_seconds": 60
            })
```

#### 9.4.2 死信队列（DLQ）

所有不可恢复或重试耗尽的 job 写入 Redis Streams 死信队列：

```
Redis Stream Key: agent.dead-letter
Entry format:
{
  "job_id": "uuid",
  "original_stream": "agent.jobs",
  "failed_at": "2026-05-02T10:05:00Z",
  "retries": 3,
  "error": "LLM API 持续返回 500",
  "job_payload": "{ ... }"
}
```

Spring 后台定时任务（每 5 分钟）消费 `agent.dead-letter`：
1. 将失败信息写入 `task_logs`（status=failed）。
2. 将对应 message 更新为 `status=failed`，前端显示 "消息发送失败"。
3. 触发告警（连续 5 个死信 → Warning，连续 20 个 → Critical）。

#### 9.4.3 超时控制

```
┌─────────────────────────────────────────────────────────────┐
│                    Agent 任务超时链                           │
│                                                             │
│  用户发送消息                                                 │
│      │                                                      │
│      ▼                                                      │
│  Spring 发布 agent.job ──────────────────────┐              │
│      │                                        │              │
│      ▼                                        ▼              │
│  SSE 超时 120s          Python 总执行超时 300s               │
│  (无事件则发 heartbeat)   (orchestrator.run 整体)             │
│      │                                        │              │
│      ▼                                        ▼              │
│  Spring 发送              发送 message_error                 │
│  message_timeout          写入 agent.dead-letter             │
│  前端展示超时提示                                           │
└─────────────────────────────────────────────────────────────┘
```

| 超时层级 | 时长 | 触发方 | 行为 |
|---------|------|--------|------|
| MCP 工具调用超时 | 30s (默认，可配置) | Spring Tool Gateway | 返回 timeout 给 Python，Python 决定是否重试 |
| LLM 单次调用超时 | 120s | Python LLM Client | 抛出 TransientError，进入重试 |
| Agent 总执行超时 | 300s | Python Worker | 取消 asyncio.Task，发 message_error + 写死信 |
| SSE 静默超时 | 120s | Spring SseStreamService | 发送 heartbeat 事件保持连接 |
| 前端 SSE 断连 | EventSource 自动重连 | 浏览器 | 重连后通过 `Last-Event-Id` 从断点续传 |

#### 9.4.4 RAG 索引失败处理

RAG 索引是异步流程，失败不会阻塞用户发送消息：

| 阶段 | 失败原因 | 处理 |
|------|---------|------|
| 文件上传 | 文件过大 / 类型不支持 | 同步返回 400，不创建 document 记录 |
| MinIO 写入 | MinIO 不可用 | 重试 3 次 → document status=failed，前端 toast 提示 |
| 文档解析 | 文件损坏 / 格式不兼容 | document status=failed，`rag_chunks` 不创建 |
| Embedding | API 超时或限流 | 重试 3 次 → 标记 document status=failed |
| Milvus 写入 | Milvus 不可用 | 重试 3 次 → 标记 document status=failed |

所有失败状态通过 `GET /api/v1/threads/:id/rag/documents` 返回给前端，前端在输入框下方的 RAG 状态 chip 中展示：

```
● 2 个文档已索引  ● 1 个索引失败 (design-notes.md: 文件损坏)
```

#### 9.4.5 降级策略

| 场景 | 降级行为 | 前端表现 |
|------|---------|---------|
| Python Agent 不可用 | Spring 返回 503，消息状态 failed | 提示 "Agent 暂不可用，请稍后重试" |
| Milvus 不可用 | RAG 检索跳过，仅用 thread 历史回复 | 回答不带 "Retrieved Context"，质量可能下降 |
| MCP 端点全部不可用 | Agent 仅用 LLM 内置知识回答 | tool_call 事件不会出现 |
| Redis 不可用 | Spring 直接返回 500 | 整体不可用 |
| Embedding API 不可用 | RAG 索引暂停，已有索引仍可检索 | 新上传文件停留在 uploaded 状态 |


---

## 10. MCP 协议网关

### 10.1 职责

MCP 网关由 Spring 负责治理，统一管理 Agent 与外部工具的连接，对应前端 IntegrationSettings 页面。Python Agent 不直接绕过治理调用生产 MCP；默认通过 Spring `/internal/tools/invoke` 统一鉴权、限流、超时、审计和日志。

```
┌────────────────────────────────────────────┐
│        Spring MCP / Tool Gateway            │
│                                            │
│  ┌──────────┐ ┌────────────┐ ┌──────────┐ │
│  │ Registry │ │ Tool Invoke│ │ Audit Log│ │
│  │ Endpoint │ │ Timeout    │ │ Trace    │ │
│  │ Health   │ │ Rate Limit │ │ Metrics  │ │
│  └────┬─────┘ └─────┬──────┘ └────┬─────┘ │
│                                            │
│       Spring AI MCP Client / Annotations   │
└────────────────────────────────────────────┘
          │              │              │
          ▼              ▼              ▼
   Python FastMCP   Java MCP Server   Remote MCP
   文件/测试/浏览器   Spring 业务工具    第三方 API
```

### 10.2 传输模式

| 传输模式 | 实现方式 | 对应前端 mock 数据 |
|---------|---------|------------------|
| stdio | Spring 或本地工具管理器启动 MCP 进程，stdin/stdout JSON-RPC；适合本地开发工具 | Node REPL |
| Streamable HTTP / SSE | Spring AI MCP Client 连接远程 MCP Server；生产优先选用 | 自定义 HTTP MCP |
| Plugin API | Spring Gateway 通过插件 SDK 或内部 adapter 调用 | Figma MCP (codex-apps) |
| IAB | Browser Worker / Python Playwright 封装为 MCP Server，Spring 只调用工具接口 | Browser Use |

### 10.3 MCP Server 写法

| 类型 | 推荐技术栈 | 适用工具 |
|------|------------|----------|
| Python FastMCP Server | 官方 MCP Python SDK / FastMCP | 文件读取、测试执行、浏览器自动化、数据处理、第三方 API 包装 |
| Java Spring AI MCP Server | Spring AI MCP Server Starter + MCP Annotations | 强依赖 Java/Spring 业务上下文、事务、企业内部 Service 的工具 |
| Remote MCP Server | Streamable HTTP / SSE | 外部系统或团队独立维护的工具服务 |

生产规则：

- MCP endpoint、transport、auth、tool schema、health status 都由 Spring `McpGatewayService` 管理。
- Python Agent 只能通过 Spring Tool Gateway 调用 MCP tool，除非是本地开发或离线测试。
- 所有 tool call 必须写入 `task_logs`，并记录 thread、agent、tool、latency、status、error。
- 真实密钥只保存在 Spring secrets service；MCP Server 只拿到运行时必要的临时凭据或 scoped token。

### 10.4 工具映射

MCP 端点注册时声明自己暴露的工具列表，Spring MCP Gateway 维护工具注册表，Python Agent 执行时读取工具 schema：

```json
{
  "name": "Figma MCP",
  "tools": ["design_context", "use_figma", "generate_diagram"],
  "schemas": {
    "design_context": {
      "input": { "file_key": "string" },
      "output": { "components": "array", "styles": "object" }
    }
  }
}
```

Python Agent 在 function calling 时只拿工具 schema 和调用入口；真正执行由 Spring Tool Gateway 代理。

---

## 11. 认证与权限

### 11.1 认证方案

```
注册/登录 → JWT (Access Token 15min + Refresh Token 7d)
    │
    ▼
前端请求自动附带 Authorization: Bearer <access_token>
    │
    ▼
Spring Security 验证 JWT → 解析 userId → 注入 request context
    │
    ▼
所有 CRUD 操作默认 scoped 到 userId（用户只能操作自己的项目）
```

### 11.2 权限模型（RBAC 预留）

当前阶段简化为单用户模式，但表结构已预留项目级别的权限：

```
users ──N:M── projects (通过 project_members 关联表)
                    │
                    ├── role: owner | editor | viewer
                    └── 预留给团队协作场景
```

---

## 12. 部署架构

### 12.1 开发环境（Docker Compose）

方案 A 的 v1 开发环境要求 `postgres`、`redis`、`minio`、`milvus`、`backend-spring` 和 `agent-python`。MCP Server 可以先按工具独立启动；Spring 通过配置注册 endpoint。当前本机如果没有已运行的 Milvus，可直接引用 Milvus 官方 Standalone Docker Compose 片段，避免手写 etcd/minio/milvus 依赖细节。

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16
    volumes: [pgdata:/var/lib/postgresql/data]

  redis:
    image: redis:7-alpine

  minio:
    image: minio/minio

  # milvus:
  #   建议从 Milvus 官方 standalone docker-compose 复制完整配置
  #   对外暴露 19530，Spring/Python 通过 MILVUS_HOST/MILVUS_PORT 连接

  backend-spring:
    build: ./backend-spring
    ports: ["8080:8080"]
    depends_on: [postgres, redis, minio]

  agent-python:
    build: ./agent-python
    depends_on: [redis, backend-spring]

  mcp-tools-python:
    build: ./mcp-tools-python
    depends_on: [backend-spring]
```

### 12.2 生产环境

```
┌─────────────────────────────────────────┐
│            Cloudflare / CDN              │
│           (前端静态资源)                   │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│          Nginx (反向代理 + SSL)          │
│   /api/* → Spring Boot BFF              │
│   /stream/* → Spring SSE                │
│   /mcp/* → Spring MCP Gateway           │
└─────────────────┬───────────────────────┘
                  │
    ┌─────────────┼─────────────┐
    ▼             ▼             ▼
┌────────────┐ ┌──────────┐ ┌──────────┐
│Spring BFF×2│ │Python    │ │MCP Server│
│+ Gateway   │ │Agent × N │ │Python/Java│
└─────┬──────┘ └────┬─────┘ └────┬─────┘
    │           │            │
    └───────────┼────────────┘
                │
    ┌───────────▼────────────┐
    │   PostgreSQL (主从)     │
    │   Redis Cluster        │
    │   MinIO / S3           │
    │   Milvus / Zilliz      │
    └────────────────────────┘
```

---

## 13. 开发路线图建议

| 阶段 | 内容 | 时长 |
|------|------|------|
| Phase 1 | Spring Boot 项目骨架、Flyway schema、只读 bootstrap，把 mock 数据搬到 PostgreSQL | 1 周 |
| Phase 2 | 前端替换 mock 读取：thread、role、skill、mcp 四类 store 接 Spring API | 1 周 |
| Phase 3 | 接入写操作：active thread、目录/会话新增、角色编辑、Skill/MCP 保存 | 1-2 周 |
| Phase 4 | Python Agent 基础服务：Redis Streams job、单 Agent LLM、Spring `SseEmitter` 流式返回 | 2 周 |
| Phase 5 | RAG 上传索引：复用输入框下方文件按钮、MinIO 存储、Python chunk/embedding、Milvus 检索、prompt 注入 | 2 周 |
| Phase 6 | Python LangGraph 多 Agent：角色路由、子 Agent 调度、交接协议、上下文压缩 | 2-3 周 |
| Phase 7 | Skill Runner：Spring 管元数据，Python 执行 skill，接入 prompt/mounts/tool binding | 1-2 周 |
| Phase 8 | MCP Gateway：Spring MCP registry、Python FastMCP tools、Java MCP Server 按需、健康检查和审计 | 2 周 |
| Phase 9 | 认证、密钥引用、多用户权限、备份与运行日志 | 1-2 周 |
| Phase 10 | 压力测试、观测、性能调优和生产部署 | 1 周 |

总计约 **14-19 周** 达到生产可用状态。前 3 个阶段完成后，前端已经可以脱离本地 mock；Phase 5 完成后，输入框下方文件按钮即可完成上传索引和问答召回；后续阶段逐步补齐真实 Agent、Skill Runner 和 MCP 能力。

---

## 14. 测试策略

### 14.1 测试金字塔

```
           ┌──────┐
           │ E2E  │  Playwright (前端已有 100 用例，保持不变)
           ├──────┤
           │ API  │  Spring MockMvc + Testcontainers (新增)
           ├──────┤
           │ 集成  │  Spring + Redis + PostgreSQL Testcontainers
           ├──────┤
           │ 单元  │  JUnit 5 + pytest (各服务独立)
           └──────┘
```

### 14.2 Spring Boot 测试

#### 单元测试 (JUnit 5)

```java
@ExtendWith(MockitoExtension.class)
class ThreadServiceTest {

    @Mock ThreadRepository threadRepo;
    @Mock RoleRepository roleRepo;
    @Mock MessageRepository messageRepo;
    @InjectMocks ThreadService threadService;

    @Test
    void createThread_shouldReturnThreadWithWelcomeMessage() {
        var folderId = UUID.randomUUID();
        when(threadRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = threadService.createThread(folderId, "会话 2", null);

        assertThat(result.label()).isEqualTo("会话 2");
        assertThat(result.roleStatus()).isEqualTo("未编排");
        verify(messageRepo, times(2)).save(any());  // 两条欢迎消息
    }

    @Test
    void updateRole_shouldSyncThreadWhenSourceThreadExists() {
        var roleId = UUID.randomUUID();
        var threadId = UUID.randomUUID();
        var role = mockRole(roleId, threadId);

        when(roleRepo.findById(roleId)).thenReturn(Optional.of(role));
        when(threadRepo.findById(threadId)).thenReturn(Optional.of(mockThread(threadId)));

        roleService.updateRole(roleId, Map.of("name", "新名称", "description", "新描述"));

        verify(threadRepo).save(argThat(t ->
            t.getLabel().equals("新名称") && t.getSummary().equals("新描述")
        ));
    }
}
```

#### API 集成测试 (Testcontainers)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class ThreadApiIntegrationTest {

    @Container static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:16");
    @Container static GenericContainer<?> redis = 
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", postgres::getJdbcUrl);
        reg.add("spring.data.redis.host", redis::getHost);
        reg.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired TestRestTemplate rest;

    @Test
    void shouldGetThreadsForFolder() {
        // 1. 注册登录
        var token = registerAndLogin("test@example.com", "password123");
        // 2. 创建项目
        var project = createProject(token, "测试项目");
        // 3. 创建目录
        var folder = createFolder(token, project.id(), "src/test");
        // 4. 获取该目录下的会话
        var threads = rest.exchange(
            "/api/v1/folders/{id}/threads", GET,
            new HttpEntity<>(authHeader(token)), ThreadListResponse.class,
            folder.id()
        );
        assertThat(threads.getBody().data().items()).isNotEmpty();
    }
}
```

#### Controller 层测试 (MockMvc)

```java
@WebMvcTest(ThreadController.class)
@Import(SecurityTestConfig.class)
class ThreadControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ThreadService threadService;

    @Test
    @WithMockUser
    void getThreads_shouldReturn200() throws Exception {
        var folderId = UUID.randomUUID();
        when(threadService.getThreadsForFolder(folderId))
            .thenReturn(List.of(mockThreadResponse()));

        mvc.perform(get("/api/v1/folders/{id}/threads", folderId)
                .header("Authorization", "Bearer test-token"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(0))
           .andExpect(jsonPath("$.data.items[0].label").value("review-agent"));
    }
}
```

### 14.3 Python 测试

#### Agent Worker 单元测试

```python
import pytest
from unittest.mock import AsyncMock, patch

@pytest.mark.asyncio
async def test_orchestrator_dispatches_to_correct_agent():
    orchestrator = AgentOrchestrator(redis=AsyncMock())
    
    job = {
        "job_id": "test-job",
        "thread_client_key": "session-review",
        "content": "检查路由守卫的边界条件",
        "role_configs": {
            "primary": {"config": {"routing": "复杂协调交给主助手"}},
            "route": {"config": {"routing": "涉及跳转链路、鉴权拦截"}}
        }
    }
    
    with patch.object(orchestrator, 'run_langgraph') as mock_run:
        await orchestrator.run(job)
        mock_run.assert_called_once()
        # 验证 router 正确选择了 route-agent
        state = mock_run.call_args[0][0]
        assert "route" in state["selected_agents"]

@pytest.mark.asyncio
async def test_context_compressor_truncates_history():
    compressor = ContextCompressor()
    long_history = [{"role": "user", "content": f"msg {i}"} for i in range(100)]
    
    result = compressor.compress(long_history, strategy="强压缩", max_tokens=500)
    
    assert len(result) <= 6  # 3 rounds = 6 messages
```

#### LangGraph 集成测试

```python
@pytest.mark.integration
async def test_langgraph_full_flow():
    """完整编排流程：主助手 → review-agent → handoff → 汇总"""
    redis = Redis.from_url("redis://localhost:6379", decode_responses=True)
    orchestrator = AgentOrchestrator(redis)
    
    job = load_test_job("fixtures/job_route_review.json")
    events = []
    
    async def collect_events():
        last_id = "0"
        while True:
            result = await redis.xread(
                {f"agent.events:{job['thread_client_key']}": last_id},
                count=10, block=30000
            )
            for stream, entries in result:
                for entry_id, fields in entries:
                    events.append(json.loads(fields["data"]))
                    last_id = entry_id
            if any(e["event"] == "message_complete" for e in events):
                break
    
    # 启动事件收集和任务执行
    await asyncio.gather(
        collect_events(),
        orchestrator.run(job)
    )
    
    # 验证事件流水线完整
    event_types = [e["event"] for e in events]
    assert "message_start" in event_types
    assert "text_delta" in event_types
    assert "message_complete" in event_types
    assert event_types.index("message_start") < event_types.index("message_complete")
```

#### RAG 索引与检索测试

```python
@pytest.mark.integration
async def test_rag_index_and_retrieve_thread_documents():
    """上传文档 → chunk → embedding → Milvus search → 返回带来源的片段"""
    indexer = RagIndexer(
        embedding_gateway=FakeEmbeddingGateway(dim=1536),
        milvus=TestMilvusClient(collection="agent_desk_chunks"),
    )
    document = load_fixture_document("fixtures/design-notes.md")

    result = await indexer.index_document(
        project_id="project-1",
        thread_id="thread-1",
        document_id="document-1",
        source=document,
    )

    assert result.status == "indexed"
    assert result.chunk_count > 0

    retriever = RagRetriever(
        embedding_gateway=FakeEmbeddingGateway(dim=1536),
        milvus=indexer.milvus,
    )
    matches = await retriever.search(
        query="权限模型有哪些约束？",
        project_id="project-1",
        thread_id="thread-1",
        top_k=6,
    )

    assert matches
    assert all(match.project_id == "project-1" for match in matches)
    assert all(match.thread_id == "thread-1" for match in matches)
    assert matches[0].source_name == "design-notes.md"
```

### 14.4 测试数据管理

- **Spring 测试**：使用 Flyway TestExtensions 自动 migrate + `@Sql` 加载测试 fixture
- **Python 测试**：使用 `fakeredis` 替代真 Redis 做单元测试；集成测试用 `testcontainers-python` 启动 Redis 和 Milvus
- **测试 fixture**：将 `ROLE_LIBRARY`、`THREAD_CONTEXTS` 导出为 JSON fixture 文件，Spring 和 Python 测试共享同一套种子数据

### 14.5 CI 流水线

```yaml
# .github/workflows/ci.yml (示意)
jobs:
  unit-test-spring:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: 21 }
      - run: ./gradlew test

  unit-test-python:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.12' }
      - run: cd agent-python && pip install -e ".[dev]" && pytest

  integration-test:
    runs-on: ubuntu-latest
    services:
      postgres: { image: postgres:16, env: { ... } }
      redis: { image: redis:7-alpine }
    steps:
      - run: ./gradlew integrationTest
      - run: cd agent-python && pytest -m integration

  e2e:
    runs-on: ubuntu-latest
    steps:
      - run: npm run test:e2e   # 现有 Playwright 测试保持不变
```

---

## 15. 监控与可观测性

### 15.1 日志规范

所有日志带 `requestId`（MDC 注入）和关键业务 ID：

```
# Spring
2026-05-02T10:00:00.123Z [http-nio-8080-exec-1] INFO  c.a.ThreadController
  - POST /threads/session-review/messages - requestId=abc123 userId=uuid threadId=uuid

# Python
2026-05-02T10:00:01.456Z [agent-worker-1] INFO app.graph.orchestrator
  - job_id=def456 thread=session-review agent=primary status=running
```

### 15.2 关键指标

| 指标 | 来源 | 说明 |
|------|------|------|
| `api_request_duration_seconds` | Spring Actuator + Micrometer | 按 route、method、status 分桶 |
| `sse_connections_active` | Spring `SseEmitterRegistry` gauge | 当前活跃 SSE 连接数 |
| `agent_job_duration_seconds` | Python Prometheus client | Agent 编排总耗时 |
| `agent_jobs_queued` | Redis `XLEN agent.jobs` | 队列积压数量 |
| `llm_call_duration_seconds` | Python | 按 provider、model 分桶 |
| `llm_token_usage_total` | Python Counter | prompt + completion tokens |
| `mcp_health_check_failures` | Spring Counter | 按 endpoint 统计 |
| `tool_invocation_errors` | Spring Counter | 按 tool、error_type 统计 |
| `rag_index_jobs_queued` | Redis `XLEN rag.index.jobs` | RAG 索引队列积压 |
| `rag_index_duration_seconds` | Python Prometheus client | 文件上传完成到 indexed 的耗时 |
| `rag_retrieval_duration_seconds` | Python + Milvus client | query embedding + Milvus search 耗时 |
| `rag_recall_empty_rate` | Python Counter | 空召回比例，用于发现过滤或文档质量问题 |
| `db_query_duration_seconds` | HikariCP + Micrometer | 数据库查询延迟 |

### 15.3 健康检查端点

```
GET /actuator/health

Response 200:
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "latency_ms": 2 } },
    "redis": { "status": "UP", "details": { "connected_clients": 5 } },
    "agentService": {
      "status": "UP",
      "details": { "workers_active": 3, "queue_depth": 12 }
    },
    "mcpGateway": {
      "status": "UP",
      "details": {
        "endpoints_total": 5,
        "endpoints_connected": 4,
        "endpoints_failed": 1
      }
    }
  }
}
```

### 15.4 告警规则

| 告警 | 条件 | 级别 |
|------|------|------|
| Agent 队列积压 | `agent_jobs_queued > 50` 持续 5 分钟 | Warning |
| Agent 队列严重积压 | `agent_jobs_queued > 200` 持续 10 分钟 | Critical |
| Agent 服务不可用 | `/actuator/health` agentService = DOWN | Critical |
| SSE 连接异常断开 | 5 分钟内重连率 > 30% | Warning |
| MCP 端点全部不可用 | `endpoints_connected == 0` | Critical |
| LLM 调用错误率 | 5 分钟内 > 10% | Warning |
| LLM 调用超时率 | 5 分钟内 > 5% | Warning |
| RAG 索引队列积压 | `rag_index_jobs_queued > 50` 持续 10 分钟 | Warning |
| RAG 检索延迟过高 | p95 `rag_retrieval_duration_seconds > 2s` 持续 10 分钟 | Warning |
| 数据库连接池耗尽 | HikariCP pending > 10 | Critical |

---

## 16. 安全加固清单

| 项 | 措施 | 优先级 |
|----|------|--------|
| API Key | 存 `secret_ref`，不落库明文；通过 Spring `SecretRefService` 解析 | P0 |
| JWT | Access Token 15min，Refresh Token 7d 且单向哈希存储 | P0 |
| CORS | Spring Security `cors()` 仅允许已知前端 origin | P0 |
| SQL 注入 | JPA 参数化查询 / MyBatis Plus `#{}` 占位符 | P0 |
| XSS | 消息 `content` 存储原始 markdown，前端渲染时做 sanitize（DOMPurify） | P1 |
| 内部 API 鉴权 | `/internal/**` 仅允许 `backend-spring` 和 `agent-python` 之间的 mTLS 或 shared secret | P1 |
| MCP 工具限流 | `POST /internal/tools/invoke` 按 endpoint + tool 限流 | P1 |
| 文件上传 | MinIO presigned URL，限制文件大小（单文件 50MB）和类型白名单 | P1 |
| RAG 权限隔离 | Milvus 查询必须带 `project_id`，默认再带 `thread_id`；禁止前端直连 Milvus | P0 |
| RAG 原文安全 | 原始文件只进 MinIO/S3；Milvus 只存 chunk、向量和必要 metadata；删除文档时同步删除向量 | P1 |
| Embedding 密钥 | Python 默认通过 Spring `/internal/embeddings/embed` 获取向量，不持久化 embedding API key | P0 |
| 审计日志 | `task_logs` 记录所有 agent_call、tool_use、role_update | P1 |
| 速率限制 | 按用户 `/api/v1/threads/:id/messages` 每分钟 20 次 | P2 |
| 密钥轮换 | `secret_ref` 支持版本号，轮换后旧版本 24 小时内仍可用 | P2 |


---

## 17. 项目流程与端到端请求生命周期

### 17.1 用户发送消息完整链路

```
时间轴 (ms)   前端                    Spring BFF                Redis                Python Agent           LLM / MCP
───────     ──────────────────────  ────────────────────────  ───────────────────  ────────────────────  ──────────────
0           用户输入 + Cmd+Enter
            POST /threads/:id/messages
                                    ─────────────────────────>
                                                              1. 鉴权 JWT
                                                              2. 校验 thread 权限
                                                              3. 写 messages 表 (role=user, status=delivered)
                                                              4. 组装 role_configs 快照
50                                                                                    XADD agent.jobs {...}
                                    <── 202 { message_id, status: "processing" } ──
53          显示 user bubble
            显示 agent "..." 占位
            建立 SSE /stream 连接 ─────────────────────────────>
                                                                                     XREAD agent.events:threadId
100                                                                                  ───────────────────────>
                                                                                                              1. RAG Retriever 判
                                                                                                                 断是否有 indexed doc
                                                                                                              2. 有: embed query
                                                                                                                 → Milvus search
                                                                                                              3. 构造 Retrieved Ctx
200                                                                                                            4. 主助手 LLM call
                                                                                                                 系统 prompt + 历史
                                                                                                                 + RAG context
                                                                                                                 + 用户消息
                                                                                                              5. LLM 决策: 需要
                                                                                                                 review-agent
500                                                                                                            6. 主助手生成子任务
                                                                                                              7. 投递 review-agent
                                                                                                                       │
                                                                                                                       ▼
                                                                                                              8. review-agent LLM
                                                                                                                 + MCP 读代码工具
800                                                                                     XADD agent.events:     ← tool_call 事件
                                                                                        threadId tool_call     ← tool_result
                                                                                                                     │
1200                                                                                                                                              
                                                                                                              9. review → primary
                                                                                                                 handoff
1500                                                                                                           10. 主助手汇总
                                                                                                                XADD text_delta × N
                                                                                    XADD text_delta... ──────→
                                    XREAD 消费 ─────────────→
                                    SSE text_delta ─────────────────────────────────────────────────────────→
1800  逐字渲染 agent bubble                                                                                    XADD message_complete
                                                                                    XADD message_complete ──→
                                    SSE message_complete ────────────────────────────────────────────────────→
2000  气泡完成，显示 token 用量
      EventSource 保持连接

图注:
- 总延迟约 1.5-3s（取决于 LLM 响应速度和工具调用次数）
- SSE text_delta 在 LLM 生成第一个 token 后即开始推送（流式输出）
- 前端 EventSource 在 message_complete 后保持连接，等待下一个消息
```

### 17.2 RAG 文档上传与索引流程

```
用户                   前端                   Spring                    Redis            Python RAG           MinIO         Milvus
│                      │                      │                         │                Worker               │              │
│  点击 composer 下方   │                      │                         │                │                    │              │
│  文件按钮             │                      │                         │                │                    │              │
│──→                   │                      │                         │                │                    │              │
│  选择文件/目录        │                      │                         │                │                    │              │
│──→                   │                      │                         │                │                    │              │
│                      │  POST /threads/:id/  │                         │                │                    │              │
│                      │  rag/uploads         │                         │                │                    │              │
│                      │  multipart/form-data │                         │                │                    │              │
│                      │─────────────────────→│                         │                │                    │              │
│                      │                      │  1. 校验文件大小、类型    │                │                    │              │
│                      │                      │  2. 上传 MinIO           │                │                    │              │
│                      │                      │─────────────────────────────────────────────────────────────→│              │
│                      │                      │←── object_key ──────────────────────────────────────────────│              │
│                      │                      │  3. 写 rag_documents      │                │                    │              │
│                      │                      │     (status=uploaded)    │                │                    │              │
│                      │                      │  4. XADD rag.index.jobs  │                │                    │              │
│                      │                      │─────────────────────────→│                │                    │              │
│                      │←── 202 { documents }─│                         │                │                    │              │
│                      │                      │                         │                │                    │              │
│  显示 toast           │                      │                         │                │                    │              │
│  "正在索引 2 个文件"   │                      │                         │                │                    │              │
│                      │                      │                         │  XREAD         │                    │              │
│                      │                      │                         │  rag.index.jobs│                    │              │
│                      │                      │                         │←───────────────│                    │              │
│                      │                      │                         │                │                    │              │
│                      │                      │                         │  5. 从 MinIO    │                    │              │
│                      │                      │                         │     下载文件    │                    │              │
│                      │                      │                         │───────────────────────────────────→│              │
│                      │                      │                         │←── file content ──────────────────│              │
│                      │                      │                         │                │                    │              │
│                      │                      │                         │  6. 解析 + chunk│                    │              │
│                      │                      │                         │     (PDF/DOCX/ │                    │              │
│                      │                      │                         │      MD/TXT等) │                    │              │
│                      │                      │                         │                │                    │              │
│                      │                      │                         │  7. 调 Spring   │                    │              │
│                      │                      │  POST /internal/       │  embed API     │                    │              │
│                      │                      │  embeddings/embed ←────│                │                    │              │
│                      │                      │──→ OpenAI API          │                │                    │              │
│                      │                      │←── vectors ───────────│                │                    │              │
│                      │                      │                        │                │                    │              │
│                      │                      │                         │  8. 写 Milvus   │                    │              │
│                      │                      │                         │──────────────────────────────────────────────────────→│
│                      │                      │                         │←── insert ok ───────────────────────────────────────│
│                      │                      │                         │                │                    │              │
│                      │                      │                         │  9. 通知 Spring │                    │              │
│                      │                      │  POST /internal/rag/   │  更新 document  │                    │              │
│                      │                      │  status (indexed) ←────│  status         │                    │              │
│                      │                      │                         │                │                    │              │
│                      │  SSE rag.status      │                         │                │                    │              │
│                      │  (或轮询 GET rag/     │                         │                │                    │              │
│                      │   documents 获取)     │                         │                │                    │              │
│                      │←─────────────────────│                         │                │                    │              │
│  更新 RAG 状态 chip   │                      │                         │                │                    │              │
│  "2 个文件已索引 ✓"   │                      │                         │                │                    │              │
```

### 17.3 角色编辑 → Thread 同步流程

```
用户在 /robot-settings?thread=session-review
│
│  修改角色 "review-agent" 的名称为 "代码审查专家"
│
├── 前端 PATCH /api/v1/roles/:id { name: "代码审查专家" }
│
├── Spring RoleService.updateRole():
│   │
│   ├── 1. 校验权限
│   ├── 2. 更新 roles 表 (name = "代码审查专家")
│   ├── 3. 更新 roles.config JSONB（合并写入）
│   ├── 4. 检测 source_thread_id 不为 null
│   │      → 在同一个事务内：
│   │         UPDATE threads SET label = "代码审查专家"
│   │         WHERE id = role.source_thread_id
│   ├── 5. 提交事务
│   └── 6. 返回 { role: {...}, synced_threads: [{ thread_id, label_updated: true }] }
│
├── 前端收到响应：
│   ├── 更新 useRoleStore 中的角色
│   └── 如果 synced_threads 非空 → 更新 useThreadStore 中对应 thread.label
│
└── 如果用户切回 / 主工作台：
    ├── thread list GET /api/v1/projects/:id/folders?include_threads=true
    └── 左侧 accordion 中对应会话标签已变为 "代码审查专家"
```

### 17.4 开发者工作流

```
┌─────────────────────────────────────────────────────────────────┐
│                     开发者日常开发流程                             │
│                                                                 │
│  1. 启动基础设施                                                   │
│     docker compose up postgres redis minio milvus               │
│                                                                 │
│  2. 启动 Spring BFF                                              │
│     cd backend-spring && ./gradlew bootRun                      │
│     验证: curl http://localhost:8080/actuator/health             │
│                                                                 │
│  3. 启动 Python Agent                                            │
│     cd agent-python && python -m app.worker                     │
│     验证: Redis Streams agent.jobs 消费者已注册                    │
│                                                                 │
│  4. 启动前端                                                      │
│     cd option-02 && npm run dev                                 │
│     验证: open http://localhost:4173                             │
│                                                                 │
│  5. 开发循环                                                      │
│     ┌────────────────────┐                                       │
│     │ 修改代码             │                                       │
│     │ ├── Spring: 自动重启 │                                       │
│     │ │   (devtools)      │                                      │
│     │ ├── Python: --reload│                                       │
│     │ └── Vue: HMR        │                                      │
│     └────────┬───────────┘                                       │
│              ▼                                                   │
│     ┌────────────────────┐                                       │
│     │ 运行测试             │                                       │
│     │ ├── ./gradlew test  │                                       │
│     │ ├── pytest          │                                      │
│     │ └── npm run test:e2e│                                       │
│     └────────┬───────────┘                                       │
│              ▼                                                   │
│     ┌────────────────────┐                                       │
│     │ 手动验证             │                                       │
│     │ ├── 浏览器操作       │                                       │
│     │ ├── curl /internal  │                                       │
│     │ └── Redis CLI 查看   │                                      │
│     │    Streams 消息      │                                      │
│     └────────────────────┘                                       │
│                                                                 │
│  6. 调试技巧                                                      │
│     - Redis: redis-cli XREAD BLOCK 0 STREAMS agent.events:* $   │
│     - Spring: actuator/httpexchanges 查看最近请求                  │
│     - Python: import pdb; pdb.set_trace() 或 logging.DEBUG       │
│     - 数据库: psql -d agentdesk -c "SELECT * FROM messages       │
│       ORDER BY created_at DESC LIMIT 5;"                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 18. 完整项目目录结构

### 18.1 仓库顶层

```
option-02/                                # 前端项目根目录（已有）
│
├── backend-spring/                       # Spring Boot BFF + 控制面（新建）
├── agent-python/                         # Python Agent 编排服务（新建）
├── mcp-tools-python/                     # Python MCP 工具集（新建）
├── mcp-tools-java/                       # Java MCP 工具集（按需新建）
├── docker/                               # Docker 相关配置
│   ├── docker-compose.yml               #   开发环境服务编排
│   ├── docker-compose.prod.yml           #   生产环境服务编排
│   ├── nginx/
│   │   └── default.conf                  #   Nginx 反向代理配置
│   └── monitoring/
│       ├── prometheus.yml               #   Prometheus 采集配置
│       └── grafana-dashboards/           #   Grafana 面板 JSON
├── docs/                                 # 文档
│   ├── BACKEND-ARCHITECTURE.md           #   本架构文档
│   ├── PRODUCT-DESIGN.md                 #   产品设计文档
│   └── api/                              #   OpenAPI 规范（可选）
│       └── openapi.yaml
├── scripts/                              # 工具脚本
│   ├── init-db.sh                       #   初始化数据库 + seed
│   ├── seed-data.sql                     #   种子数据 SQL
│   └── health-check.sh                  #   全栈健康检查
└── .github/workflows/                    # CI/CD
    └── ci.yml
```

### 18.2 Spring Boot 服务完整结构

```
backend-spring/
├── build.gradle                          # Gradle 构建（或 pom.xml）
├── settings.gradle
├── gradle.properties
├── Dockerfile
│
├── src/main/java/com/agentdesk/
│   ├── AgentDeskApplication.java         # @SpringBootApplication 入口
│   │
│   ├── config/
│   │   ├── SecurityConfig.java           # Spring Security + JWT + CORS
│   │   ├── RedisConfig.java              # Redis 连接、序列化配置
│   │   ├── SseConfig.java                # SseEmitter 超时、线程池
│   │   ├── FlywayConfig.java             # Flyway migration 配置
│   │   ├── JacksonConfig.java            # JSON 序列化（驼峰/蛇形、日期）
│   │   ├── InternalAuthConfig.java       # /internal/** 共享密钥鉴权
│   │   ├── McpClientConfig.java          # Spring AI MCP Client 配置
│   │   └── WebMvcConfig.java             # CORS、拦截器注册
│   │
│   ├── security/
│   │   ├── JwtTokenProvider.java         # JWT 签发/验证
│   │   ├── JwtAuthenticationFilter.java  # OncePerRequestFilter
│   │   ├── InternalAuthFilter.java       # /internal/** 鉴权过滤器
│   │   └── UserDetailsServiceImpl.java   # 用户加载
│   │
│   ├── controller/
│   │   ├── AuthController.java           # 注册/登录/刷新 token
│   │   ├── BootstrapController.java      # GET /projects/:id/bootstrap
│   │   ├── PreferenceController.java     # GET|PATCH /me/preferences
│   │   ├── FolderController.java         # 目录 CRUD + sync-roles
│   │   ├── ThreadController.java         # 会话 CRUD + /stream SSE
│   │   ├── MessageController.java        # POST 消息 + GET 历史
│   │   ├── RoleController.java           # 角色 CRUD
│   │   ├── SkillController.java          # Skill 管理 + toggle
│   │   ├── McpEndpointController.java    # MCP 端点 CRUD + 健康检查
│   │   ├── RagDocumentController.java    # RAG 上传 + 列表 + 搜索
│   │   ├── HealthStatusController.java   # 运行健康摘要（对应前端 HEALTH_ITEMS）
│   │   └── TaskLogController.java        # GET /logs（运行日志查询）
│   │
│   ├── controller/internal/
│   │   ├── AgentInternalController.java  # /internal/agent/*
│   │   ├── ToolInternalController.java   # /internal/tools/*
│   │   ├── SkillInternalController.java  # /internal/skills/*
│   │   ├── EmbeddingInternalController.java # /internal/embeddings/*
│   │   └── RagInternalController.java    # /internal/rag/*
│   │
│   ├── service/
│   │   ├── ProjectService.java           # 项目查询
│   │   ├── UserPreferenceService.java    # 用户偏好读写
│   │   ├── FolderService.java            # 目录管理 + 默认会话创建
│   │   ├── ThreadService.java            # 会话管理、label/summary 联动更新
│   │   ├── MessageService.java           # 消息持久化、历史查询、游标分页
│   │   ├── RoleService.java              # 角色 CRUD、source_thread_id 联动
│   │   ├── SkillRegistryService.java     # Skill 元数据管理
│   │   ├── McpGatewayService.java        # MCP 端点注册、连接管理
│   │   ├── ToolInvocationService.java     # 工具调用代理（路由到不同 transport）
│   │   ├── RagDocumentService.java       # RAG 文档管理、状态更新
│   │   ├── EmbeddingGatewayService.java  # Embedding API 调用（持有 API key）
│   │   ├── RagIndexJobPublisher.java     # 发布 rag.index.jobs 到 Redis
│   │   ├── AgentJobPublisher.java        # 发布 agent.jobs 到 Redis
│   │   ├── SseStreamService.java         # SSE 连接管理、事件推送
│   │   ├── SecretRefService.java         # secret_ref 解析（Vault/AWS/GCP）
│   │   ├── TaskLogService.java           # 运行日志写入与查询
│   │   └── HealthAggregationService.java # 健康摘要聚合
│   │
│   ├── model/
│   │   ├── entity/                       # JPA Entity 或 MyBatis Plus Model
│   │   │   ├── User.java
│   │   │   ├── Project.java
│   │   │   ├── ProjectMember.java
│   │   │   ├── Folder.java
│   │   │   ├── Thread.java
│   │   │   ├── Message.java
│   │   │   ├── Role.java
│   │   │   ├── ThreadRole.java
│   │   │   ├── Skill.java
│   │   │   ├── PromptMountPolicy.java
│   │   │   ├── McpEndpoint.java
│   │   │   ├── McpHealthCheck.java
│   │   │   ├── RagDocument.java
│   │   │   ├── RagChunk.java
│   │   │   ├── TaskLog.java
│   │   │   └── UserPreference.java
│   │   │
│   │   ├── dto/                          # 请求/响应 DTO
│   │   │   ├── request/
│   │   │   │   ├── SendMessageRequest.java
│   │   │   │   ├── CreateFolderRequest.java
│   │   │   │   ├── CreateThreadRequest.java
│   │   │   │   ├── UpdateRoleRequest.java
│   │   │   │   └── ... (对应各 API 请求体)
│   │   │   └── response/
│   │   │       ├── ApiResponse.java      # 统一响应包装 { code, message, data, request_id }
│   │   │       ├── PagedResponse.java    # 分页响应
│   │   │       ├── BootstrapResponse.java
│   │   │       └── ... (对应各 API 响应体)
│   │   │
│   │   └── event/                        # Redis Streams 事件模型
│   │       ├── AgentJobPayload.java
│   │       ├── AgentEventPayload.java
│   │       ├── RagIndexJobPayload.java
│   │       └── ToolCallResult.java
│   │
│   ├── repository/                       # JPA Repository 或 MyBatis Mapper
│   │   ├── UserRepository.java
│   │   ├── ProjectRepository.java
│   │   ├── FolderRepository.java
│   │   ├── ThreadRepository.java
│   │   ├── MessageRepository.java
│   │   ├── RoleRepository.java
│   │   ├── SkillRepository.java
│   │   ├── McpEndpointRepository.java
│   │   ├── RagDocumentRepository.java
│   │   ├── TaskLogRepository.java
│   │   └── UserPreferenceRepository.java
│   │
│   ├── infrastructure/
│   │   ├── redis/
│   │   │   ├── RedisStreamTemplate.java  # Redis Streams 读写封装
│   │   │   └── DeadLetterConsumer.java   # 死信队列定时消费
│   │   └── mcp/
│   │       ├── StdioTransportManager.java # stdio MCP 进程生命周期管理
│   │       ├── HttpTransportClient.java   # HTTP/SSE MCP 客户端
│   │       └── McpToolSchemaRegistry.java # 工具 schema 注册表
│   │
│   └── exception/
│       ├── GlobalExceptionHandler.java   # @ControllerAdvice
│       ├── ErrorCode.java               # 错误码枚举
│       ├── ResourceNotFoundException.java
│       └── AgentUnavailableException.java
│
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/migration/
│       ├── V1__init_schema.sql
│       ├── V2__seed_data.sql
│       └── V3__add_rag_tables.sql
│
├── src/test/java/com/agentdesk/
│   ├── unit/
│   │   ├── service/
│   │   │   ├── ThreadServiceTest.java
│   │   │   ├── RoleServiceTest.java
│   │   │   ├── MessageServiceTest.java
│   │   │   └── ...
│   │   └── controller/
│   │       ├── ThreadControllerTest.java
│   │       └── ...
│   ├── integration/
│   │   ├── ThreadApiIntegrationTest.java
│   │   ├── RoleSyncIntegrationTest.java
│   │   ├── RagUploadIntegrationTest.java
│   │   └── SseStreamIntegrationTest.java
│   └── fixtures/
│       └── test-data.sql
│
└── gradle/                               # Gradle wrapper
    └── wrapper/
```

### 18.3 Python Agent 服务完整结构

```
agent-python/
├── pyproject.toml                        # 项目元数据 + 依赖
├── Dockerfile
├── README.md
│
├── app/
│   ├── __init__.py
│   ├── main.py                           # FastAPI 内部 API 入口
│   ├── worker.py                         # Redis Streams agent.jobs + rag.index.jobs 消费者
│   │
│   ├── graph/                            # LangGraph 编排
│   │   ├── __init__.py
│   │   ├── orchestrator.py               # 主状态图（plan → dispatch → execute → aggregate）
│   │   ├── state.py                      # AgentState TypedDict 定义
│   │   ├── router.py                     # 角色路由决策
│   │   ├── handoff.py                    # 子 Agent 交接协议
│   │   └── compressor.py                 # 上下文压缩（轻/中/强/关闭）
│   │
│   ├── agents/                           # 子 Agent 实现
│   │   ├── __init__.py
│   │   ├── base.py                       # Agent 基类（含 tool calling 循环）
│   │   ├── primary.py                    # 主助手（orchestrator）
│   │   ├── review.py                     # 代码审查 agent
│   │   ├── test.py                       # 测试 agent
│   │   ├── route.py                      # 路由 agent
│   │   ├── snapshot.py                   # 快照 agent
│   │   └── auth.py                       # 认证 agent
│   │
│   ├── skills/                           # Skill 执行
│   │   ├── __init__.py
│   │   ├── runner.py                     # Skill Runner：加载 manifest → 组合 prompt → 执行
│   │   ├── registry.py                   # 本地 skill 注册（从 Spring 同步）
│   │   ├── manifest.py                   # skill manifest 解析
│   │   └── prompt_loader.py              # prompt 模板加载 + 变量注入
│   │
│   ├── rag/                              # RAG 检索与索引
│   │   ├── __init__.py
│   │   ├── indexer.py                    # 文档解析 → chunk → embedding → Milvus
│   │   ├── retriever.py                  # query embedding → Milvus search → 片段组装
│   │   ├── reranker.py                   # 可选 rerank（v1 留空）
│   │   ├── loaders/                      # 各类文档解析器
│   │   │   ├── __init__.py
│   │   │   ├── pdf.py
│   │   │   ├── docx.py
│   │   │   ├── markdown.py
│   │   │   ├── text.py
│   │   │   └── code.py
│   │   └── prompt_context.py             # Retrieved Context → prompt 注入模板
│   │
│   ├── llm/                              # LLM 调用抽象
│   │   ├── __init__.py
│   │   ├── client.py                     # LLM 统一客户端（统一接口）
│   │   ├── providers/
│   │   │   ├── __init__.py
│   │   │   ├── openai_provider.py        # OpenAI / Azure OpenAI
│   │   │   ├── anthropic_provider.py     # Anthropic Claude
│   │   │   └── google_provider.py        # Google Gemini
│   │   └── token_counter.py              # token 计数（tiktoken / anthropic tokenizer）
│   │
│   ├── gateway/                          # 调用 Spring 内部 API
│   │   ├── __init__.py
│   │   ├── spring_client.py              # HTTP client（shared secret 鉴权）
│   │   ├── tool_invoke.py                # 调用 /internal/tools/invoke
│   │   └── embed.py                      # 调用 /internal/embeddings/embed
│   │
│   ├── events/                           # Redis Streams 事件发布
│   │   ├── __init__.py
│   │   └── publisher.py                  # XADD agent.events:{threadId}
│   │
│   └── utils/
│       ├── __init__.py
│       ├── redis_client.py               # Redis 连接池管理
│       ├── logging_config.py             # 结构化日志配置
│       └── metrics.py                    # Prometheus 指标注册
│
├── tests/
│   ├── __init__.py
│   ├── conftest.py                       # pytest fixtures（fakeredis, mock Spring 等）
│   ├── unit/
│   │   ├── test_orchestrator.py
│   │   ├── test_router.py
│   │   ├── test_compressor.py
│   │   ├── test_handoff.py
│   │   ├── test_rag_indexer.py
│   │   └── test_rag_retriever.py
│   ├── integration/
│   │   ├── test_langgraph_full_flow.py
│   │   ├── test_rag_index_retrieve.py
│   │   └── test_spring_gateway.py
│   └── fixtures/
│       ├── job_route_review.json
│       ├── job_rag_query.json
│       └── design-notes.md
│
└── skills-definitions/                   # Skill 定义文件（Python 侧）
    ├── frontend-design/
    │   ├── manifest.yaml
    │   └── prompts/
    │       ├── system.txt
    │       └── task.txt
    ├── browser-use/
    │   ├── manifest.yaml
    │   └── prompts/
    └── openai-docs/
        ├── manifest.yaml
        └── prompts/
```

### 18.4 MCP 工具集结构

```
mcp-tools-python/
├── pyproject.toml
├── Dockerfile
├── app/
│   ├── __init__.py
│   ├── server.py                         # FastMCP Server 入口
│   ├── tools/
│   │   ├── __init__.py
│   │   ├── file_tools.py                 # 读代码、对比变更、列断言
│   │   ├── browser_tools.py              # goto、click、screenshot、domSnapshot
│   │   ├── test_tools.py                 # 执行测试、整理 smoke
│   │   └── data_tools.py                 # 数据处理、API 调用
│   └── resources/
│       └── templates.py                  # MCP Resource 模板
│
└── tests/
    └── test_tools.py

mcp-tools-java/                           # 按需创建，存放依赖 Spring 上下文的工具
├── build.gradle
├── Dockerfile
└── src/main/java/com/agentdesk/mcp/
    ├── McpServerApplication.java
    └── tools/
        ├── SpringContextTool.java        # 查询 Spring Bean / 配置
        └── DatabaseQueryTool.java         # 受控数据库查询工具
```

### 18.5 前端 API 接入结构

前端已有 `src/views`、`src/stores` 和 `src/data/index.js`。后端接入时不建议在 Vue 页面里直接写 `fetch`，而是在 `src/api` 封装 HTTP，再由 Pinia store 调用。

```
src/
├── api/                                  # 新增：后端 API adapter
│   ├── http.js                           # fetch 包装、鉴权、错误处理、snake/camel 转换
│   ├── bootstrap.js                      # GET /projects/:id/bootstrap
│   ├── threads.js                        # folders、threads、messages、SSE
│   ├── roles.js                          # role CRUD、sync-roles
│   ├── skills.js                         # skill CRUD、toggle、sync-policy
│   ├── mcp.js                            # endpoint CRUD、health checks
│   ├── rag.js                            # upload、documents、jobs、search
│   └── settings.js                       # preferences、logs、backups、tool permissions
│
├── services/                             # 新增：浏览器侧业务适配
│   ├── threadHydrator.js                 # bootstrap → Pinia thread state
│   ├── sseClient.js                      # EventSource 连接、断点续传、事件分发
│   ├── uploadQueue.js                    # RAG 上传进度和取消
│   └── idempotency.js                    # client_message_id / X-Idempotency-Key 生成
│
├── stores/                               # 保留：页面状态和本地兜底
│   ├── thread.js                         # 调用 api/threads，不再直接写 mock
│   ├── role.js                           # 调用 api/roles
│   ├── skill.js                          # 调用 api/skills
│   └── mcp.js                            # 调用 api/mcp
│
└── data/index.js                         # v1 接入后只作为 seed 生成来源，不再作为运行时真数据
```

迁移顺序：

1. 新增 `src/api/http.js`，先接 `GET /api/v1/projects/:id/bootstrap`。
2. `useThreadStore.hydrateFromRoute()` 改为异步读取 bootstrap；失败时才回落 `localStorage`。
3. 消息发送接 `threads.sendMessage()` 和 `sseClient.connect(threadKey)`。
4. Role/Skill/MCP 页面把 modal `onSave` 替换为对应 PATCH/POST。
5. RAG 上传按钮接 `rag.uploadDocuments(threadId, files)`，上传状态交给 `uploadQueue` 管理。

---

## 19. 本地开发环境搭建指南

### 19.1 前置要求

| 工具 | 最低版本 | 用途 |
|------|---------|------|
| JDK | 21 | Spring Boot 编译运行 |
| Python | 3.12 | Agent 服务运行 |
| Node.js | 18+ | 前端开发（已有） |
| Docker Desktop | 最新稳定版 | 运行 PostgreSQL、Redis、MinIO、Milvus |
| Gradle | 8.x（wrapper 自带） | Spring Boot 构建 |
| psql | 16 | 数据库调试（可选） |
| redis-cli | 7.x | Redis 调试（可选） |

### 19.2 第一步：启动基础设施

```bash
# 在项目根目录
cd /Users/yangzhecheng/Desktop/agents/option-02

# 创建 docker 目录（如果不存在）
mkdir -p docker

# 编写 docker-compose.yml（见下方）
# 启动基础设施
docker compose -f docker/docker-compose.yml up -d postgres redis minio

# Milvus Standalone 建议使用官方 docker-compose：
# wget https://github.com/milvus-io/milvus/releases/download/v2.4.0/milvus-standalone-docker-compose.yml
# docker compose -f milvus-standalone-docker-compose.yml up -d
```

`docker/docker-compose.yml` 核心内容：

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: agentdesk
      POSTGRES_USER: agentdesk
      POSTGRES_PASSWORD: change-me-dev
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    command: redis-server --appendonly yes

  minio:
    image: minio/minio:latest
    ports: ["9000:9000", "9001:9001"]
    environment:
      MINIO_ROOT_USER: agentdesk-dev
      MINIO_ROOT_PASSWORD: change-me-dev
    command: server /data --console-address ":9001"
    volumes: [minio_data:/data]

volumes:
  pgdata:
  minio_data:
```

### 19.3 第二步：初始化数据库

```bash
# Spring Boot 启动时 Flyway 自动执行 migration
# 也可以手动执行种子数据：
cd backend-spring
./gradlew flywayMigrate

# 验证表结构：
psql -h localhost -U agentdesk -d agentdesk -c "\dt"
# 预期输出：users, projects, folders, threads, messages, roles, thread_roles,
#           skills, mcp_endpoints, mcp_health_checks, rag_documents, rag_chunks, task_logs

# 插入种子数据（把 ROLE_LIBRARY + THREAD_CONTEXTS 搬入数据库）：
psql -h localhost -U agentdesk -d agentdesk -f ../scripts/seed-data.sql
```

### 19.4 第三步：启动 Spring Boot

```bash
cd backend-spring

# 开发 profile 配置（application-dev.yml）已在 7.5 节给出
export DB_USERNAME=agentdesk
export DB_PASSWORD=change-me-dev
export REDIS_HOST=localhost
export INTERNAL_SECRET=dev-secret-change-me-in-production

./gradlew bootRun
# 验证：curl http://localhost:8080/actuator/health
```

### 19.5 第四步：启动 Python Agent

```bash
cd agent-python

# 创建虚拟环境
python3.12 -m venv .venv
source .venv/bin/activate

# 安装依赖
pip install -e ".[dev]"

# 配置环境变量
export REDIS_HOST=localhost
export INTERNAL_SECRET=dev-secret-change-me-in-production
export INTERNAL_SPRING_URL=http://localhost:8080
export LOG_LEVEL=DEBUG

# 启动 Worker（消费 agent.jobs 和 rag.index.jobs）
python -m app.worker

# 另开终端，启动内部 API（供 Spring 调用 /internal/agent/jobs）
python -m app.main
# 默认监听 http://localhost:8001
# 验证：curl http://localhost:8001/internal/health/workers
```

### 19.6 第五步：启动前端

```bash
cd /Users/yangzhecheng/Desktop/agents/option-02

# 将前端 API 地址指向本地 Spring
# 在 vite.config.js 中添加 proxy（或直接修改 .env）：
#   server.proxy: { '/api': 'http://localhost:8080' }

# 使用 AGENTS.md 中已验证的 Node 路径
PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run dev

# 访问 http://localhost:4173
```

### 19.7 验证全栈连通

```bash
# 一键健康检查脚本（scripts/health-check.sh）
#!/bin/bash
set -e

echo "=== Agent Desk 全栈健康检查 ==="

# 1. 基础设施
echo -n "PostgreSQL: " && pg_isready -h localhost -U agentdesk -d agentdesk && echo "OK" || echo "FAIL"
echo -n "Redis: " && redis-cli ping && echo "OK" || echo "FAIL"

# 2. Spring Boot
echo -n "Spring Boot: " && curl -sf http://localhost:8080/actuator/health | jq .status || echo "FAIL"

# 3. Python Agent
echo -n "Python Agent: " && curl -sf http://localhost:8001/internal/health/workers | jq .status || echo "FAIL"

# 4. 前端
echo -n "Frontend: " && curl -sf http://localhost:4173 > /dev/null && echo "OK" || echo "FAIL"

echo "=== 检查完成 ==="
```

### 19.8 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| Spring 启动报 "Connection refused" | PostgreSQL/Redis 未启动 | `docker compose up -d postgres redis` |
| Flyway migration 失败 | 数据库不存在 | `docker compose exec postgres createdb -U agentdesk agentdesk` |
| Python `redis.exceptions.ConnectionError` | Redis 未启动或 host 不对 | 检查 `REDIS_HOST` 环境变量 |
| 前端 API 返回 404 | Vite proxy 未配置 | 在 `vite.config.js` 添加 proxy 配置 |
| SSE 连接立即断开 | Spring SseEmitter 超时 | 检查 `spring.mvc.async.request-timeout` 配置 |
| MCP 工具调用 403 | `/internal/**` shared secret 不匹配 | 确保 Spring 和 Python 的 `INTERNAL_SECRET` 相同 |
| Milvus 连接失败 | Milvus 未启动或版本不兼容 | 确认使用 Milvus 2.4+ Standalone |

---

## 20. 前端接入契约与用户交互细节

本节把当前 Vue 页面、Pinia store、DOM id 和后端 API 绑定起来。前端可以继续保留现有组件结构，但所有 mock 写入要逐步替换成 API adapter。

### 20.1 全局前端 API 约定

| 项 | 约定 |
|----|------|
| API 前缀 | 所有浏览器请求使用 `/api/v1`；Vite 开发环境通过 proxy 转发到 Spring |
| 认证 | `Authorization: Bearer <access_token>`；登录态过期时先尝试 refresh，失败后跳登录 |
| 请求 ID | Spring 每个响应返回 `request_id`；前端日志和 toast 错误详情保留该 id |
| 幂等 | 所有用户触发的写操作都应带 `X-Idempotency-Key`，消息发送使用 `client_message_id` |
| 字段命名 | 后端 JSON 使用 `snake_case`；前端 API adapter 统一映射为当前 store 使用的 camelCase |
| 时间格式 | 后端使用 ISO-8601 UTC；前端展示本地时间或现有文案，如 `04:02 / 当前原型` |
| 错误格式 | 统一 `{ code, message, details, request_id }`；前端 toast 显示 `message`，调试面板保留 `details` |
| 本地兜底 | `localStorage` 只用于离线兜底和未接后端阶段，不覆盖服务端返回的最终状态 |

前端建议新增一个薄 adapter 层，避免各页面直接散落 `fetch`：

```text
src/api/
├── http.js              # auth header、request_id、错误处理、snake/camel 转换
├── bootstrap.js         # getProjectBootstrap
├── threads.js           # folders、threads、messages、stream
├── roles.js             # role CRUD + sync
├── skills.js            # skill CRUD/toggle
├── mcp.js               # endpoint CRUD/health
├── rag.js               # upload/list/jobs/search
└── settings.js          # preferences、compression、backup、logs、permissions
```

### 20.2 路由、线程和首屏初始化

当前路由只有四个页面：

| 路由 | 页面组件 | 后端初始化需求 |
|------|----------|----------------|
| `/` | `MainWorkspace.vue` | `GET /api/v1/projects/:id/bootstrap`，返回 folders、threads、最近消息、active thread、RAG 状态 |
| `/settings` | `SystemSettings.vue` | `GET /api/v1/me/preferences` + `GET /api/v1/projects/:id/logs?limit=20` |
| `/robot-settings?thread=:threadKey` | `RobotSettings.vue` | 根据 query thread 解析当前 thread，再返回同目录 thread roles |
| `/integration` | `IntegrationSettings.vue` | `GET /api/v1/projects/:id/skills` + `GET /api/v1/projects/:id/mcp` + `GET /api/v1/mcp/health-status` |

线程解析必须保持当前前端行为：

1. `TopNav.robotHref` 从当前工作台传入 `robotThreadId`，生成 `/robot-settings?thread=${activeContext.id}`。
2. 后端 bootstrap 接收可选 `?thread=<client_key>`，先校验该 thread 是否属于当前用户项目。
3. 若 query thread 合法，返回 `active_thread_key=queryThread`，并更新用户偏好。
4. 若 query thread 不合法，读取 `user_configs.active_thread_key`。
5. 若仍无合法值，回退 `session-review`。

Bootstrap 推荐返回结构：

```json
{
  "project": { "id": "uuid", "name": "默认工作区" },
  "active_thread_key": "session-review",
  "preferences": {
    "layout_density": "紧凑",
    "execution_guard": "开启",
    "composer_mode": "context_first"
  },
  "folders": [
    {
      "id": "uuid",
      "name": "src/auth",
      "sort_order": 0,
      "thread_keys": ["session-review", "session-auth"]
    }
  ],
  "threads": {
    "session-review": {
      "id": "uuid",
      "client_key": "session-review",
      "folder": "src/auth",
      "file": "src/auth/useSession.ts",
      "label": "review-agent",
      "summary": "正在梳理 session 状态变更点",
      "roles": ["primary", "review", "auth"],
      "focus_role": "review",
      "role_status": "已编排",
      "session_role_id": "thread-role-session-review",
      "rag": {
        "document_count": 2,
        "indexed_count": 2,
        "latest_status": "indexed"
      }
    }
  },
  "recent_messages": {
    "session-review": [
      {
        "id": "uuid",
        "role": "agent",
        "agent_name": "主助手",
        "content": "优先看 src/auth、src/router 和 tests...",
        "status": "completed",
        "created_at": "2026-05-02T10:00:00Z"
      }
    ]
  },
  "roles": { "primary": { "id": "uuid", "name": "主助手", "config": {} } },
  "skills": [],
  "mcp_endpoints": [],
  "health_items": []
}
```

前端 adapter 映射规则：

| 后端字段 | 当前前端字段 |
|----------|--------------|
| `thread.client_key` | `context.id` |
| `thread.folder` | `context.folder` |
| `thread.file` | `context.file` |
| `thread.focus_role` | `context.focusRole` |
| `thread.role_status` | `context.roleStatus` |
| `thread.session_role_id` | `context.sessionRoleId` |
| `folders[].thread_keys` | `fileGroups[].threads` |
| `recent_messages[threadKey][]` | `conversations[threadKey]` |

### 20.3 主工作台交互契约

| UI 元素 | 当前前端方法 | 后端接入动作 | 成功 UI | 失败 UI |
|---------|--------------|--------------|---------|---------|
| 左侧“新增关联目录” + `#related-folder-input` | `openFolderPicker()` → `addRelatedFolder(files)` | 从 `FileList[0].webkitRelativePath` 推导目录名，调用 `POST /api/v1/projects/:id/folders` | 新目录置顶、自动选中默认 thread、toast `已新增关联目录` | toast `目录创建失败`，保留当前 thread |
| accordion summary | `selectThread(threadId)` | `PATCH /api/v1/me/preferences { active_thread_key }` | 展开该目录，更新 `#composer-thread-chip` | 偏好保存失败不阻塞切换，但 toast 提示 |
| “新增会话” | `addThread(group)` | `POST /api/v1/folders/:id/threads` | 新 thread 加入当前目录并选中 | toast `新增会话失败` |
| 发送按钮 `#composer-send` | `sendMessage()` | `POST /api/v1/threads/:id/messages`，随后连接 SSE | 立即显示 user bubble 和 agent 占位 | 空内容提示；请求失败显示重试 |
| 快捷发送 `Cmd/Ctrl+Enter` | `onComposerKeydown()` | 与发送按钮相同 | 与发送按钮相同 | 与发送按钮相同 |
| 文件按钮 `#composer-attach-file` | 当前 `attachFolder()` | 后端接入后打开文件选择器，调用 `POST /api/v1/threads/:id/rag/uploads` | `#composer-context-chip` 显示索引状态 | 文件过大/类型不支持/索引失败时显示 toast |
| 终端按钮 `#composer-insert-terminal` | `insertTerminal()` | v1 继续前端插入文本；后续可接 `POST /api/v1/threads/:id/context-snippets` | composer 内插入终端输出 | 无 |
| 机器人设置导航 | `TopNav.robotHref` | 保持 `/robot-settings?thread=<client_key>` | 机器人设置页加载同目录角色 | query 无效时回退默认 thread |

创建关联目录请求：

```http
POST /api/v1/projects/:id/folders
Content-Type: application/json
X-Idempotency-Key: folder_<hash>

{
  "name": "src/auth",
  "source": {
    "type": "browser_directory_picker",
    "first_relative_path": "src/auth/useSession.ts",
    "file_count": 12
  },
  "default_thread": {
    "label": "primary-agent",
    "summary": "新关联目录已加入，等待补充任务目标",
    "role_keys": ["primary", "review"],
    "focus_role_key": "primary"
  }
}
```

发送消息前端状态机：

| 状态 | 触发 | UI |
|------|------|----|
| `idle` | 默认 | composer 可输入，发送按钮可点 |
| `validating` | 点击发送 | 空文本直接 toast，不发请求 |
| `submitting` | POST messages 中 | 禁用发送按钮，保留输入内容直到 202 |
| `streaming` | 收到 202/message_start | 清空 composer，显示 agent 占位，追加 `text_delta` |
| `completed` | `message_complete` | 气泡完成，记录 token 用量 |
| `failed` | POST 失败或 SSE error | 气泡失败态，提供“重试发送” |

### 20.4 机器人设置页交互契约

`RobotSettings.vue` 当前通过 `threadStore.getThreadIdsForFolder(context.folder)` 找出同目录所有会话，再用 `roleStore.ensureThreadRole(threadContext)` 生成会话角色。后端接入后，这个逻辑由 `GET /api/v1/folders/:id/roles?include_thread_roles=true` 或 `POST /api/v1/folders/:id/sync-roles` 承接。

| UI 操作 | Modal 字段 | 后端 PATCH |
|---------|------------|------------|
| 切换模型 `openModelConfig` | `provider`、`official_url`、`api_key`、`endpoint`、`api_format`、`model`、`model_mapping`、`config_json` | `PATCH /api/v1/roles/:id`，`api_key` 只进入密钥服务，响应返回 `secret_ref` |
| 调整压缩强度 | `compression` | `PATCH /api/v1/roles/:id { "config": { "compression": "中压缩" } }` |
| 修改角色说明 | `name`、`alias`、`description`、`matrix_copy` | `PATCH /api/v1/roles/:id`；若有 `source_thread_id`，事务内同步 `threads.label/summary` |
| 编辑提示前缀 | `prompt_prefix`、`prompt_layers[]` | `PATCH /api/v1/roles/:id { "config": { ... } }` |
| 职责勾选 | `duties[]` | `PATCH /api/v1/roles/:id { "config": { "duties": [] } }` |
| 自定义职责输入 | `custom_duty` | debounce 后 PATCH，或离焦时保存 |
| 复制当前配置 | 无写入 | 可选 `GET /api/v1/roles/:id/export` 返回脱敏快照 |

模型配置保存请求示例：

```json
{
  "name": "review-agent",
  "alias": "review-guard",
  "config": {
    "provider": "OpenAI",
    "official_url": "https://platform.openai.com/docs",
    "model": "GPT-5.4-mini",
    "endpoint": "https://api.openai.com/v1/responses",
    "api_format": "OpenAI Responses",
    "model_mapping": "review-agent -> gpt-5.4-mini",
    "config_json": { "reasoning_effort": "low", "temperature": 0.1 },
    "compression": "轻压缩",
    "prompt_prefix": "先定位风险，再给出最短可执行建议",
    "prompt_layers": ["系统层", "角色层"],
    "tools": ["读代码", "对比变更", "列回归点"],
    "handoff": "只回传风险和缺口，不替主助手直接给最终答复"
  },
  "secret_input": {
    "type": "api_key",
    "value": "<redacted-by-frontend-before-log>"
  }
}
```

响应必须脱敏：

```json
{
  "role": {
    "id": "uuid",
    "name": "review-agent",
    "config": {
      "provider": "OpenAI",
      "secret_ref": "secret://project/openai/review",
      "api_key_mask": "****review",
      "model": "GPT-5.4-mini"
    }
  },
  "synced_threads": [
    { "thread_id": "uuid", "thread_client_key": "session-review", "label_updated": true, "summary_updated": true }
  ]
}
```

### 20.5 系统设置页交互契约

`SystemSettings.vue` 当前主要是平台入口原型，后端接入后不应只弹 modal 文案，而要映射到真实配置和日志。

| UI 区域 | 当前交互 | 后端 API | 数据字段 |
|---------|----------|----------|----------|
| 默认布局密度 | `selectSegment('density', value)` | `PATCH /api/v1/me/preferences` | `preferences.layout_density = "紧凑" / "舒展"` |
| 执行边界 | `selectSegment('guard', value)` | `PATCH /api/v1/me/preferences` | `preferences.execution_guard = "开启" / "关闭"` |
| 上下文压缩 | `openContextModal('compression-settings')` | `POST /api/v1/threads/:id/context/compress` | `compression_level`、`preserve_items[]` |
| 内容备份 | `openContextModal('backup-settings')` | `POST /api/v1/threads/:id/backups` | `include[]`、`target_uri`、`name_rule` |
| 任务队列 | `openPlatformCard('open-task-queue')` | `GET /api/v1/projects/:id/tasks?status=queued,running,blocked` | `items[]` |
| 运行日志 | `openPlatformCard('open-run-log')` | `GET /api/v1/projects/:id/logs` | `level`、`type`、`thread_id`、`cursor` |
| 工具授权 | `openPlatformCard('open-tool-permission')` | `GET/PATCH /api/v1/projects/:id/tool-permissions` | role/tool allowlist |
| 记忆与备份 | `openPlatformCard('open-memory-backup')` | `GET /api/v1/projects/:id/backups` | backup snapshots |

上下文压缩请求：

```json
{
  "thread_id": "uuid",
  "compression_level": "中压缩",
  "preserve_items": ["当前目录", "最近操作链", "角色分工", "关键结论"],
  "create_backup_before_compress": true
}
```

响应：

```json
{
  "compression_job_id": "uuid",
  "status": "queued",
  "backup_id": "uuid",
  "estimated_tokens_before": 11800,
  "target_tokens_after": 2200
}
```

### 20.6 Skill 与 MCP 页面交互契约

Skill 不是直接执行代码的服务，而是 Prompt/策略/工具组合包。MCP endpoint 才是工具入口。

| UI 操作 | 后端 API | 请求重点 | 成功 UI |
|---------|----------|----------|---------|
| 新增 Skill | `POST /api/v1/projects/:id/skills` | `name/source/scope/status/mounts` | skill 卡片插到列表顶部 |
| 编辑 Skill | `PATCH /api/v1/skills/:id` | JSON merge，`mounts` 标准化数组 | toast `Skill 配置已保存` |
| 启停 Skill | `POST /api/v1/skills/:id/toggle` | 不传 body 或传 `next_status` | 按钮文案切换 |
| 同步装载策略 | `POST /api/v1/projects/:id/skills/sync-policy` | 当前 project + enabled skills | 更新 `last_run_at` 和 health |
| 新增 MCP endpoint | `POST /api/v1/projects/:id/mcp` | `name/transport/auth_type/url/tools` | endpoint 卡片插到顶部 |
| 编辑 MCP endpoint | `PATCH /api/v1/mcp/:id` | 不返回明文密钥 | toast `MCP 接口已保存` |
| 单个检查 | `POST /api/v1/mcp/:id/health-check` | timeout 可选 | 更新 status/latency |
| 批量检查 | `POST /api/v1/mcp/health-check-all` | project scope | 所有 endpoint 更新状态 |

Skill 请求示例：

```json
{
  "name": "frontend-design",
  "source": "local skill",
  "scope": "页面设计、交互细节、前端视觉 polish",
  "status": "启用",
  "mounts": ["系统层", "任务层"],
  "config": {
    "auto_mount": true,
    "requires_rag": false,
    "allowed_roles": ["primary", "review"]
  }
}
```

MCP endpoint 请求示例：

```json
{
  "name": "Browser Use",
  "transport": "iab",
  "auth_type": "local session",
  "url": "file:// / localhost",
  "tools": ["goto", "click", "screenshot", "domSnapshot"],
  "health_config": {
    "timeout_ms": 5000,
    "check_tools_list": true
  },
  "secret_input": null
}
```

### 20.7 前端状态缓存和冲突处理

| 数据 | 当前存储 | 后端接入后策略 |
|------|----------|----------------|
| 当前 thread | `agentDesk.activeThreadId` | 作为启动兜底；服务端 `active_thread_key` 返回后覆盖 |
| thread/context/conversation | `agentDesk.threadState.v1` | 后端接入后只缓存最近一次 bootstrap，写操作以后端响应为准 |
| role 编辑 | `agentDesk.roleState.v1` | 未登录/离线时可暂存草稿；登录后提示用户合并或丢弃 |
| Skill/MCP | 当前仅内存 | 后端接入后不写 localStorage，刷新从 API 拉取 |
| RAG 上传状态 | 无 | 使用服务端 document/job 状态；可在 sessionStorage 缓存上传进度，但刷新后以 API 为准 |

冲突处理规则：

1. `PATCH` 请求必须带 `updated_at` 或 `version`，后端检测到版本落后返回 `409`。
2. 前端收到 `409` 时弹出“服务端已有更新”，提供“覆盖保存 / 放弃本地修改 / 查看差异”。
3. 对于 role config 这类 JSONB，后端做字段级 merge；数组字段如 `tools`、`duties`、`prompt_layers` 以客户端传入完整数组为准。
4. 对消息发送和 RAG 上传，使用幂等 key 去重，不弹冲突框。

### 20.8 前端验收清单

| 场景 | 验收点 |
|------|--------|
| 直接访问 `/robot-settings?thread=route-test` | 选中 `route-test` 所在目录，左侧角色列表来自同目录所有会话 |
| 切换主工作台 thread | URL 不强制变化，但用户偏好保存，刷新后仍停在该 thread |
| 新增关联目录 | 只创建 folder/thread，不触发 RAG 索引 |
| 点击输入框下方文件按钮 | 上传文件并创建 RAG index job，不创建新目录 |
| 发送消息 | user bubble 立即出现，agent bubble 通过 SSE 流式增长 |
| SSE 断线重连 | 不重复 user bubble，不丢失已产生的 `text_delta` |
| 角色改名 | `/robot-settings` 当前角色名更新，同时工作台会话卡片 label 联动 |
| Skill 启停 | 按钮文案和 status chip 与后端状态一致 |
| MCP 健康检查 | status、latency、health summary 同步更新 |
| 上下文压缩 | 创建 compress job，必要时先生成 backup |
