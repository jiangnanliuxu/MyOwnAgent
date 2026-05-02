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
- 每个阶段完成后必须立即提交并推送到 Git；推送成功且 `git status` 干净后，Planning Agent 必须默认自动把下一阶段切到 `In Progress` 并开始规划。
- 如果提交或推送失败，当前阶段保持未关闭，必须先修复 Git/远端问题，不得继续开发下一阶段。
- 只有用户明确要求暂停、只提交不继续或等待确认时，阶段完成后才不自动进入下一阶段；暂停原因必须记录在本日志。
- 所有阶段都要记录测试命令；如果某项测试不能运行，必须写明原因和剩余风险。

## 当前状态

| 当前阶段 | 状态 | 阻塞项 | 下一步 |
|----------|------|--------|--------|
| B06 | In Progress | 无 | Planning Agent 开始 Role 编排阶段 |

## 阶段拆分

| 阶段 | 模块 | 范围 | Planning Done | Dev Done | Test Done | Bug 状态 | 备注 |
|------|------|------|---------------|----------|-----------|----------|------|
| B00 | 流程初始化 | 建立三 Agent 流程、开发日志、AGENTS.md 规则 | [x] | [x] | [x] | 无 | 文档和日志初始化完成 |
| B01 | Spring Boot 骨架 | `backend-spring` 项目、Gradle、基础配置、健康检查、统一响应、异常处理、会话当前 Agent 状态显示 | [x] | [x] | [x] | 无 | 先不接业务表 |
| B02 | 基础设施与数据库 | Docker Compose、PostgreSQL、Redis、MinIO、Flyway V1 schema | [x] | [x] | [x] | 无 | Milvus 只提供独立启动说明 |
| B03 | 认证与用户偏好 | Auth、JWT、`/auth/me`、`/me/preferences`、active thread 规则 | [x] | [x] | [x] | 无 | 保持 query/localStorage/default 兼容 |
| B04 | Bootstrap 只读接口 | `GET /projects/:id/bootstrap`，迁移 mock seed 到 PostgreSQL | [x] | [x] | [x] | 无 | 前端可先只读接入 |
| B05 | Folder/Thread/Message CRUD | 目录新增、会话新增、消息历史、幂等消息发送入队前半段 | [x] | [x] | [x] | 无 | 暂不启用真实 Agent |
| B06 | Role 编排 | roles、thread_roles、sync-roles、source_thread_id 回写 thread | [x] | [ ] | [ ] | In Progress | 机器人设置页核心 |
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

### B02 - 基础设施与数据库

- Planning Agent:
  - [x] 阶段范围已确认：Docker Compose 启动 PostgreSQL 16、Redis 7、MinIO；Spring 接入 datasource/Flyway/Redis/MinIO 配置；Flyway V1 只建 schema。
  - [x] 验收标准已确认：默认 profile 不强依赖外部基础设施；dev profile 可连接 compose；`./gradlew clean test` 不需要 Docker 也能通过。
  - [x] 依赖和风险已记录：mock seed 留到 B04；Milvus 不进入 B02 主 compose；`gen_random_uuid()` 需要 `pgcrypto` extension。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成
  - [x] 自测命令已运行
  - 变更文件：
    - `docker/docker-compose.yml`
    - `docker/README.md`
    - `backend-spring/build.gradle`
    - `backend-spring/src/main/resources/application.yml`
    - `backend-spring/src/main/resources/application-dev.yml`
    - `backend-spring/src/main/resources/db/migration/V1__init_schema.sql`
    - `backend-spring/src/main/java/com/agentdesk/backend/config/InfrastructureProperties.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/AgentDeskBackendApplicationTests.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/config/FlywayMigrationTest.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/config/InfrastructurePropertiesTest.java`
    - `README.md`
  - 自测命令：
    - `docker compose -f docker/docker-compose.yml config`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && ./gradlew bootJar`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过：默认 profile 运行时健康检查通过
  - 测试命令：
    - `docker compose -f docker/docker-compose.yml config`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd backend-spring && ./gradlew bootRun --args='--server.port=18080'`
    - `curl -s -i http://localhost:18080/actuator/health`
    - `curl -s -i -H 'X-Request-Id: b02-default' http://localhost:18080/api/v1/health`
    - `docker compose -f docker/docker-compose.yml down -v && docker compose -f docker/docker-compose.yml up -d postgres redis minio`
    - `docker compose -f docker/docker-compose.yml exec -T postgres pg_isready -U agentdesk -d agentdesk`
    - `docker compose -f docker/docker-compose.yml exec -T redis redis-cli ping`
    - `curl -sf http://localhost:9000/minio/health/live`
    - `SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
    - `SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'`
    - `docker compose -f docker/docker-compose.yml exec -T postgres psql -U agentdesk -d agentdesk -Atc "select version, success from flyway_schema_history order by installed_rank;"`
    - `docker compose -f docker/docker-compose.yml exec -T postgres psql -U agentdesk -d agentdesk -Atc "select tablename from pg_tables where schemaname='public' order by tablename;"`
    - `docker compose -f docker/docker-compose.yml exec -T redis redis-cli XADD b02.smoke '*' type ping`
  - 测试结果：
    - Compose 静态配置：通过，包含 `postgres`、`redis`、`minio`、`minio-init`
    - 后端 `./gradlew clean test`：通过
    - 后端 `./gradlew bootJar`：通过
    - 默认 profile `bootRun`：通过，不连接 PostgreSQL/Redis/Flyway
    - `/actuator/health`：200，`UP`
    - `/api/v1/health`：200，返回 `code=0`、`message=ok`、`request_id=b02-default`
    - Docker daemon：启动后可用，Docker Server `29.4.0`
    - PostgreSQL：`pg_isready` 通过，Flyway `V1` success=`t`
    - Redis：`PING` 返回 `PONG`，Stream 写入/读取/删除通过
    - MinIO：健康检查通过，`minio-init` 创建 `agent-desk-dev` bucket
    - dev profile：`SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks` 通过
    - dev profile `/actuator/health`：200，包含 `db=UP` 和 `redis=UP`
    - schema 表清单：包含 `users`、`projects`、`user_configs`、`folders`、`threads`、`messages`、`roles`、`thread_roles`、`skills`、`mcp_endpoints`、`mcp_health_checks`、`task_logs`、`rag_documents`、`rag_chunks`、`rag_index_jobs`
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - Redis dev 改为无密码，匹配本地 `redis-cli ping` 验收命令。
    - 新增 `minio-init` 服务创建 `agent-desk-dev` bucket。
    - Flyway schema test 补齐 `user_configs`、`mcp_health_checks` 和 `streamable_http` 覆盖。
    - 调整默认 profile 断言测试，使其在 dev profile 下跳过，避免 dev 连接测试误判。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B03 - 认证与用户偏好

- Planning Agent:
  - [x] 阶段范围已确认：Spring Security/JWT、注册/登录/刷新、`/api/v1/auth/me`、`/api/v1/me/preferences` 读写。
  - [x] 验收标准已确认：默认 profile 不依赖 Docker；dev profile 可连接 B02 数据库；未认证返回统一 401；偏好 active thread 按 query/user/default 规则落地。
  - [x] 依赖和风险已记录：V1 缺少密码与 refresh token 字段，B03 需新增 V2 migration；B04 seed 前不能假设已有 thread 数据。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/build.gradle`
    - `backend-spring/src/main/resources/application.yml`
    - `backend-spring/src/main/resources/db/migration/V2__auth_tokens.sql`
    - `backend-spring/src/main/java/com/agentdesk/backend/auth/**`
    - `backend-spring/src/main/java/com/agentdesk/backend/security/**`
    - `backend-spring/src/main/java/com/agentdesk/backend/user/**`
    - `backend-spring/src/main/java/com/agentdesk/backend/common/error/AuthenticationException.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/common/error/ErrorCode.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/common/error/GlobalExceptionHandler.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/auth/AuthControllerTest.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/common/error/GlobalExceptionHandlerTest.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/config/FlywayMigrationTest.java`
  - 自测命令：
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.auth.AuthControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'`
    - `curl -s -i http://localhost:18080/actuator/health`
    - `POST /api/v1/auth/register`
    - `POST /api/v1/auth/login`
    - `GET /api/v1/auth/me`
    - `GET /api/v1/me/preferences`
    - `PATCH /api/v1/me/preferences`
    - `POST /api/v1/auth/refresh`
    - `GET /api/v1/auth/me` without token
    - `docker compose -f docker/docker-compose.yml exec -T postgres psql -U agentdesk -d agentdesk -Atc "select version, success from flyway_schema_history order by installed_rank;"`
  - 测试结果：
    - 默认 profile `./gradlew clean test`：通过
    - B03 定向 `AuthControllerTest`：通过
    - dev profile `SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`：通过
    - `./gradlew bootJar`：通过
    - dev profile Flyway：`V1` 和 `V2` success=`t`
    - `user_auth_credentials`、`refresh_tokens` 已创建
    - 注册/登录返回 `access_token`、`refresh_token`、`token_type=Bearer`
    - `/api/v1/auth/me` 带 token 返回当前用户，不带 token 返回统一 `UNAUTHORIZED`
    - `/api/v1/me/preferences` 首次返回 `active_thread_key=session-review`
    - `PATCH /api/v1/me/preferences` 支持 JSON 合并，未知 thread 回退 `session-review`
    - refresh token 轮换通过，旧 refresh token 复用返回 `UNAUTHORIZED`
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - refresh token 默认 TTL 调整为 7 天，匹配架构文档。
    - `PATCH /me/preferences` 改为合并 `preferences` JSON，而不是整体替换。
    - 显式放行 `OPTIONS /**`，避免 CORS preflight 被认证拦截。
    - 增加拒绝式 `UserDetailsService`，避免 Spring Boot 生成默认用户密码日志。
    - 顺序重跑 dev profile 测试，避免并行 `clean` 删除另一个测试进程的 build 输出。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B04 - Bootstrap 只读接口

- Planning Agent:
  - [x] 阶段范围已确认：实现 `GET /api/v1/projects/:id/bootstrap`，返回 folders、threads、recent messages、roles、skills、mcp endpoints、user preferences 和当前 active thread。
  - [x] 验收标准已确认：默认 profile 可用内存 seed；dev profile 可把 mock 等价 seed 写入 PostgreSQL；接口只读、需要认证、只能访问当前用户项目。
  - [x] 依赖和风险已记录：B04 不实现 folder/thread/message 写接口；mock seed 需覆盖 `thread/role/skill/mcp` store 的基础字段，并保留 `session-review` 默认线程。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B04 无新增 Flyway 迁移，bootstrap 首次读取时按项目幂等写入 PostgreSQL seed。
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/src/main/java/com/agentdesk/backend/bootstrap/**`
    - `backend-spring/src/main/java/com/agentdesk/backend/auth/AuthRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/auth/AuthResponse.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/auth/AuthService.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/auth/InMemoryAuthRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/auth/JdbcAuthRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/auth/UserResponse.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/common/error/ErrorCode.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/security/SecurityConfig.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/bootstrap/BootstrapControllerTest.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/auth/AuthControllerTest.java`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.bootstrap.BootstrapControllerTest`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.bootstrap.BootstrapControllerTest`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.bootstrap.BootstrapControllerTest --rerun-tasks`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'`
    - `POST /api/v1/auth/register`
    - `GET /api/v1/projects/:id/bootstrap`
    - `GET /api/v1/projects/:id/bootstrap?thread=route-test`
    - `GET /api/v1/me/preferences`
    - `GET /api/v1/projects/:id/bootstrap` without token
    - `GET /api/v1/projects/:otherProjectId/bootstrap` with another user's token
    - `docker compose -f docker/docker-compose.yml exec -T postgres psql -U agentdesk -d agentdesk -Atc "select version, success from flyway_schema_history order by installed_rank;"`
  - 测试结果：
    - B04 定向 `BootstrapControllerTest`：通过
    - 默认 profile `./gradlew clean test`：通过
    - dev profile `SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`：通过
    - `./gradlew bootJar`：通过
    - HTTP 冒烟：注册返回 `default_project_id`，bootstrap 返回 folders/threads/roles/skills/mcp/health_items
    - `?thread=route-test` 优先并持久化到 `/me/preferences`
    - 无 token 返回 `UNAUTHORIZED`，跨用户 project 返回 `FORBIDDEN`
    - 响应不包含 `password_hash`、`refresh_token`、`api_key`
    - dev profile Flyway：`V1` 和 `V2` success=`t`
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - `SecurityConfig` 补充保护 `GET /api/v1/projects/*/bootstrap`。
    - 注册/登录/`/auth/me` 响应补充 `default_project_id`，让前端后续能调用 project-scoped bootstrap。
    - dev profile seed 增加 `seed_sort` metadata，修复 PostgreSQL 返回 thread 顺序与前端 mock 不一致的问题。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B05 - Folder/Thread/Message CRUD

- Planning Agent:
  - [x] 阶段范围已确认：实现项目目录列表/新增、目录下会话列表/新增、会话详情/更新、消息历史分页和消息发送入库的前半段。
  - [x] 验收标准已确认：所有写接口需要认证、项目归属校验和幂等保护；消息发送只写 user message 和 pending agent 占位，不启动真实 Agent/SSE。
  - [x] 依赖和风险已记录：依赖 B04 seed 数据和 `default_project_id`；B05 不实现真实 Agent 编排、Redis job、SSE 流式输出或 RAG 上传。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B05 无新增 Flyway 迁移，复用 B01 schema 和 B04 seed。
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/**`
    - `backend-spring/src/main/java/com/agentdesk/backend/bootstrap/BootstrapResponse.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/bootstrap/BootstrapSeedData.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/bootstrap/InMemoryBootstrapRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/bootstrap/JdbcBootstrapRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/security/SecurityConfig.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/workspace/WorkspaceControllerTest.java`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --tests com.agentdesk.backend.bootstrap.BootstrapControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --tests com.agentdesk.backend.bootstrap.BootstrapControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'`
    - `GET /api/v1/projects/:id/folders?include_threads=true`
    - `POST /api/v1/projects/:id/folders`
    - `POST /api/v1/folders/:id/threads`
    - `PATCH /api/v1/threads/:id`
    - `GET /api/v1/threads/:id/messages?limit=1`
    - `POST /api/v1/threads/:id/messages` with `X-Idempotency-Key`
    - `GET /api/v1/folders/:id/threads` with another user's token
  - 测试结果：
    - B05 定向 `WorkspaceControllerTest`：通过
    - 默认 profile `./gradlew clean test`：通过
    - dev profile `SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`：通过
    - `./gradlew bootJar`：通过
    - HTTP 冒烟：目录列表、新增目录、新增会话、更新会话、消息分页、消息发送幂等、跨用户 403 均通过
    - dev PostgreSQL 检查：`smoke-msg-1` user message 1 条，`smoke-msg-1:agent` pending placeholder 1 条
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - 修复 Java lambda 捕获递增变量导致的编译失败。
    - `MessageView` 补充 `client_message_id`，满足消息幂等和前端对齐契约。
    - `SecurityConfig` 补充保护 `/api/v1/projects/*/folders`、`/api/v1/folders/**`、`/api/v1/threads/**`。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B06 - Role 编排

- Planning Agent:
  - [x] 阶段范围已确认：实现项目角色列表、角色详情/更新、thread_roles 同步、folder sync-roles，以及 `source_thread_id` 角色更新回写 thread `label/summary`。
  - [x] 验收标准已确认：接口需要认证和 project/thread/folder 归属校验；角色配置 JSONB 采用合并更新；同步逻辑不启动 Agent。
  - [x] 依赖和风险已记录：依赖 B04/B05 的 seed、folder/thread 查询和 session role key；B06 不实现 Skill、MCP 或 SSE。
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
