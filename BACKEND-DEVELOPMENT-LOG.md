# Backend Development Log

> 后端分段开发跟踪表。每一段必须先完成开发，再由测试 Agent 验证；测试无 bug 后才能进入下一段。

## Agent 分工

| 子 Agent | 职责 | 产出 |
|----------|------|------|
| Planning Agent | 拆分阶段、定义验收标准、协调开发顺序、维护本日志 | 阶段计划、依赖说明、风险记录、下一步指令 |
| Development Agent | 按当前阶段实现代码、修复测试发现的问题、更新实现说明 | 代码变更、迁移脚本、接口实现、修复记录 |
| Testing Agent | 为当前阶段编写/运行测试，验证无回归后放行 | 测试用例、测试命令、测试结果、bug 列表 |

## 闸门规则

- 同一时间只允许一个后端开发阶段处于 `In Progress`。
- Development Agent 完成当前阶段后，在本日志勾选 `Dev Done`，并写清楚变更文件和验证入口。
- Testing Agent 必须在同一阶段完成测试后，才能勾选 `Test Done`。
- 如果 Testing Agent 发现 bug，当前阶段保持未完成，Development Agent 必须先修复，再交回 Testing Agent 复测。
- 只有 `Dev Done` 和 `Test Done` 都打勾，Planning Agent 才能启动下一阶段。
- 所有阶段都要记录测试命令；如果某项测试不能运行，必须写明原因和剩余风险。

## 当前状态

| 当前阶段 | 状态 | 阻塞项 | 下一步 |
|----------|------|--------|--------|
| B01 | Done | 无 | 进入 B02 基础设施与数据库规划 |

## 阶段拆分

| 阶段 | 模块 | 范围 | Planning Done | Dev Done | Test Done | Bug 状态 | 备注 |
|------|------|------|---------------|----------|-----------|----------|------|
| B00 | 流程初始化 | 建立三 Agent 流程、开发日志、AGENTS.md 规则 | [x] | [x] | [x] | 无 | 文档和日志初始化完成 |
| B01 | Spring Boot 骨架 | `backend-spring` 项目、Gradle、基础配置、健康检查、统一响应、异常处理、会话当前 Agent 状态显示 | [x] | [x] | [x] | 无 | 先不接业务表 |
| B02 | 基础设施与数据库 | Docker Compose、PostgreSQL、Redis、MinIO、Flyway V1 schema | [ ] | [ ] | [ ] | 待开始 | Milvus 可先独立启动 |
| B03 | 认证与用户偏好 | Auth、JWT、`/auth/me`、`/me/preferences`、active thread 规则 | [ ] | [ ] | [ ] | 待开始 | 保持 query/localStorage/default 兼容 |
| B04 | Bootstrap 只读接口 | `GET /projects/:id/bootstrap`，迁移 mock seed 到 PostgreSQL | [ ] | [ ] | [ ] | 待开始 | 前端可先只读接入 |
| B05 | Folder/Thread/Message CRUD | 目录新增、会话新增、消息历史、幂等消息发送入队前半段 | [ ] | [ ] | [ ] | 待开始 | 暂不启用真实 Agent |
| B06 | Role 编排 | roles、thread_roles、sync-roles、source_thread_id 回写 thread | [ ] | [ ] | [ ] | 待开始 | 机器人设置页核心 |
| B07 | Skill 管理 | skills CRUD、toggle、mount policy、sync-policy | [ ] | [ ] | [ ] | 待开始 | Skill 不是 MCP |
| B08 | MCP Gateway | mcp_endpoints、health-check、tool registry、`/internal/tools/invoke` | [ ] | [ ] | [ ] | 待开始 | Python 不能绕过 Spring 调工具 |
| B09 | SSE 与 Agent Job | `agent.jobs`、`agent.events:{threadId}`、SseEmitter、断点续传 | [ ] | [ ] | [ ] | 待开始 | 先接单 Agent mock worker |
| B10 | Python Agent 基础服务 | `agent-python`、FastAPI internal API、Redis worker、LangGraph skeleton | [ ] | [ ] | [ ] | 待开始 | 不直接写核心业务表 |
| B11 | RAG 上传索引 | `rag_documents`、`rag.index.jobs`、MinIO、Milvus、Embedding Gateway | [ ] | [ ] | [ ] | 待开始 | 输入框下方文件按钮是入口 |
| B12 | RAG 检索回答 | query embedding、Milvus search、prompt 注入、`rag_retrieval` SSE | [ ] | [ ] | [ ] | 待开始 | 默认 scope=thread |
| B13 | 多 Agent 编排 | 角色路由、handoff、上下文压缩、Skill Runner 调用 | [ ] | [ ] | [ ] | 待开始 | 依赖 B09/B10 |
| B14 | 系统设置能力 | 任务队列、运行日志、备份、上下文压缩、工具授权 | [ ] | [ ] | [ ] | 待开始 | 对应 `/settings` |
| B15 | 前端 API 接入 | `src/api`、`src/services`、Pinia store 替换 mock、SSE/RAG 上传 | [ ] | [ ] | [ ] | 待开始 | 分页面逐步切换 |
| B16 | 观测、安全与部署 | metrics、告警、权限、密钥、Docker prod、CI | [ ] | [ ] | [ ] | 待开始 | 生产前收口 |

## 阶段记录模板

复制下面模板到“阶段执行记录”中使用。

```markdown
### BXX - 模块名

- Planning Agent:
  - [ ] 阶段范围已确认
  - [ ] 验收标准已确认
  - [ ] 依赖和风险已记录
- Development Agent:
  - [ ] 代码实现完成
  - [ ] 数据库迁移/配置更新完成
  - [ ] 自测命令已运行
  - 变更文件：
  - 自测命令：
- Testing Agent:
  - [ ] 单元测试通过
  - [ ] 集成测试通过
  - [ ] 回归测试通过
  - 测试命令：
  - 测试结果：
- Bugs:
  - [ ] 无阻塞 bug
  - 修复记录：
- Gate:
  - [ ] Dev Done
  - [ ] Test Done
  - [ ] Planning Agent 已批准进入下一阶段
```

## 阶段执行记录

### B00 - 流程初始化

- Planning Agent:
  - [x] 阶段范围已确认
  - [x] 验收标准已确认
  - [x] 依赖和风险已记录
- Development Agent:
  - [x] `AGENTS.md` 更新三 Agent 流程规则
  - [x] `BACKEND-DEVELOPMENT-LOG.md` 创建阶段跟踪表
  - 变更文件：
    - `AGENTS.md`
    - `BACKEND-DEVELOPMENT-LOG.md`
  - 自测命令：
    - `python3` 检查 Markdown fence 是否闭合
- Testing Agent:
  - [x] 文档结构检查通过
  - [x] 未发现未闭合代码块
  - 测试结果：文档类检查通过
- Bugs:
  - [x] 无阻塞 bug
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B01 - Spring Boot 骨架

- Planning Agent:
  - [x] 阶段范围已确认：`backend-spring` Spring Boot 3.x 骨架、健康检查、统一响应、异常处理、request id、dev CORS
  - [x] 验收标准已确认：不依赖 PostgreSQL/Redis/MinIO/Milvus，`/actuator/health` 和 `/api/v1/health` 可运行
  - [x] 依赖和风险已记录：B02 前不接业务表；API 响应必须保持 `code/message/data/request_id`
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B01 无数据库迁移，新增 `application.yml` 和 `application-dev.yml`
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/build.gradle`
    - `backend-spring/settings.gradle`
    - `backend-spring/gradlew`
    - `backend-spring/gradlew.bat`
    - `backend-spring/gradle/wrapper/gradle-wrapper.jar`
    - `backend-spring/gradle/wrapper/gradle-wrapper.properties`
    - `backend-spring/src/main/java/com/agentdesk/backend/**`
    - `backend-spring/src/main/resources/application.yml`
    - `backend-spring/src/main/resources/application-dev.yml`
    - `backend-spring/src/test/java/com/agentdesk/backend/**`
    - `src/views/MainWorkspace.vue`
    - `src/assets/global.css`
    - `tests/main-workspace.spec.js`
  - 自测命令：
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && ./gradlew bootJar`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run build`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:unit`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:e2e`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd backend-spring && ./gradlew bootRun --args='--server.port=18080'`
    - `curl -s -i http://localhost:18080/actuator/health`
    - `curl -s -i -H 'X-Request-Id: smoke-health' http://localhost:18080/api/v1/health`
    - `curl -s -i http://localhost:18080/api/v1/__not_found__`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run build`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:unit`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:e2e`
  - 测试结果：
    - 后端 `./gradlew clean test`：7 tests passed
    - 后端 `./gradlew bootJar`：通过
    - `/actuator/health`：200，`UP`
    - `/api/v1/health`：200，返回 `code=0`、`message=ok`、`request_id=smoke-health`
    - `/api/v1/__not_found__`：404，返回统一错误格式
    - 前端 build：通过
    - Vitest：4 files / 6 tests passed
    - Playwright：101 passed
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - 修正后端健康检查路径为 `/api/v1/health`，保持 `/api/v1` 前缀一致。
    - 修正统一响应格式为 `{ code, message, data, request_id }` / `{ code, message, details, request_id }`。
    - 为 `WebMvcTest` 注册 `CorsProperties`，修复异常处理测试上下文启动失败。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段
