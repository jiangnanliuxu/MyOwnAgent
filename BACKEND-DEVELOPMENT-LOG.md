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
| 全部阶段 | Completed | 无 | B16 已完成，等待下一轮需求规划 |

## 阶段拆分

| 阶段 | 模块 | 范围 | Planning Done | Dev Done | Test Done | Bug 状态 | 备注 |
|------|------|------|---------------|----------|-----------|----------|------|
| B00 | 流程初始化 | 建立三 Agent 流程、开发日志、AGENTS.md 规则 | [x] | [x] | [x] | 无 | 文档和日志初始化完成 |
| B01 | Spring Boot 骨架 | `backend-spring` 项目、Gradle、基础配置、健康检查、统一响应、异常处理、会话当前 Agent 状态显示 | [x] | [x] | [x] | 无 | 先不接业务表 |
| B02 | 基础设施与数据库 | Docker Compose、PostgreSQL、Redis、MinIO、Flyway V1 schema | [x] | [x] | [x] | 无 | Milvus 只提供独立启动说明 |
| B03 | 认证与用户偏好 | Auth、JWT、`/auth/me`、`/me/preferences`、active thread 规则 | [x] | [x] | [x] | 无 | 保持 query/localStorage/default 兼容 |
| B04 | Bootstrap 只读接口 | `GET /projects/:id/bootstrap`，迁移 mock seed 到 PostgreSQL | [x] | [x] | [x] | 无 | 前端可先只读接入 |
| B05 | Folder/Thread/Message CRUD | 目录新增、会话新增、消息历史、幂等消息发送入队前半段 | [x] | [x] | [x] | 无 | 暂不启用真实 Agent |
| B06 | Role 编排 | roles、thread_roles、sync-roles、source_thread_id 回写 thread | [x] | [x] | [x] | 无 | 机器人设置页核心 |
| B07 | Skill 管理 | skills CRUD、toggle、mount policy、sync-policy | [x] | [x] | [x] | 无 | Skill 不是 MCP |
| B08 | MCP Gateway | mcp_endpoints、health-check、tool registry、`/internal/tools/invoke` | [x] | [x] | [x] | 无 | Python 不能绕过 Spring 调工具 |
| B09 | SSE 与 Agent Job | `agent.jobs`、`agent.events:{threadId}`、SseEmitter、断点续传 | [x] | [x] | [x] | 无 | 先接单 Agent mock worker |
| B10 | Python Agent 基础服务 | `agent-python`、FastAPI internal API、Redis worker、LangGraph skeleton | [x] | [x] | [x] | 无 | 不直接写核心业务表 |
| B11 | RAG 上传索引 | `rag_documents`、`rag.index.jobs`、MinIO、Milvus、Embedding Gateway | [x] | [x] | [x] | 无 | 输入框下方文件按钮是入口 |
| B12 | RAG 检索回答 | query embedding、Milvus search、prompt 注入、`rag_retrieval` SSE | [x] | [x] | [x] | 无 | 默认 scope=thread |
| B13 | 多 Agent 编排 | 角色路由、handoff、上下文压缩、Skill Runner 调用 | [x] | [x] | [x] | 无 | 依赖 B09/B10 |
| B14 | 系统设置能力 | 任务队列、运行日志、备份、上下文压缩、工具授权 | [x] | [x] | [x] | 无 | 对应 `/settings` |
| B15 | 前端 API 接入 | `src/api`、`src/services`、Pinia store 替换 mock、SSE/RAG 上传 | [x] | [x] | [x] | 无 | 分页面逐步切换 |
| B16 | 观测、安全与部署 | metrics、告警、权限、密钥、Docker prod、CI | [x] | [x] | [x] | 无 | 生产前收口完成 |

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
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B06 无新增 Flyway 迁移，复用 `roles`、`threads`、`thread_roles`、`folders` 表。
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/src/main/java/com/agentdesk/backend/role/**`
    - `backend-spring/src/main/java/com/agentdesk/backend/security/SecurityConfig.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/role/RoleControllerTest.java`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.role.RoleControllerTest`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.role.RoleControllerTest`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'`
    - `GET /api/v1/projects/:id/roles?include_thread_roles=true`
    - `GET /api/v1/roles/:id`
    - `PATCH /api/v1/roles/:id`
    - `POST /api/v1/folders/:id/sync-roles`
    - `GET /api/v1/threads/:id`
    - `GET /api/v1/roles/:id` with another user's token
  - 测试结果：
    - B06 定向 `RoleControllerTest`：通过
    - B05+B06 组合回归：通过
    - 默认 profile `./gradlew clean test`：通过
    - dev profile `SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`：通过
    - `./gradlew bootJar`：通过
    - HTTP 冒烟：角色列表、角色详情、角色更新、source thread 回写、sync-roles、跨用户 403 均通过
    - `config.api_key` 不落库响应，更新后只返回 `secret_ref`
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - 角色列表和 sync-roles 测试改为内容匹配，不依赖 PostgreSQL 与内存实现的返回排序。
    - default profile 的角色 source thread 同步在 role 模块状态中验证；dev profile HTTP 冒烟额外验证 `GET /threads/:id` 已回写。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B07 - Skill 管理

- Planning Agent:
  - [x] 阶段范围已确认：实现项目 Skill 列表、Skill 详情/更新、启停 toggle、Prompt mount policy 保存，以及项目级 sync-policy。
  - [x] 验收标准已确认：接口需要认证和 project 归属校验；Skill 仍是 Prompt/策略/工具组合包，不混同 MCP endpoint。
  - [x] 依赖和风险已记录：依赖 B04 seed 的 `skills` 数据；B07 不执行真实 Python Skill Runner，不调用 MCP 工具。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B07 无新增 Flyway 迁移，复用 `skills` 表。
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/src/main/java/com/agentdesk/backend/skill/**`
    - `backend-spring/src/main/java/com/agentdesk/backend/security/SecurityConfig.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/skill/SkillControllerTest.java`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.skill.SkillControllerTest`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.skill.SkillControllerTest`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'`
    - `GET /api/v1/projects/:id/skills`
    - `POST /api/v1/projects/:id/skills`
    - `PATCH /api/v1/skills/:id`
    - `POST /api/v1/skills/:id/toggle`
    - `POST /api/v1/projects/:id/skills/sync-policy`
    - `GET /api/v1/skills/:id` with another user's token
  - 测试结果：
    - B07 定向 `SkillControllerTest`：通过
    - B05+B06+B07 组合回归：通过
    - 默认 profile `./gradlew clean test`：通过
    - dev profile `SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`：通过
    - `./gradlew bootJar`：通过
    - HTTP 冒烟：Skill 列表、新增、配置更新、启停、sync-policy、跨用户 403 均通过
    - `mounts` 标准化去重，`config` 采用 JSON merge，sync-policy 只更新启用中的 Skill
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - B07 暂不实现真实 Python Skill Runner，只更新时间和策略状态，避免混淆 Skill 与 MCP 执行边界。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B08 - MCP Gateway

- Planning Agent:
  - [x] 阶段范围已确认：实现项目 MCP endpoint 列表、新增、更新、单端点健康检查、批量健康检查、工具注册只读视图和 `/internal/tools/invoke` 占位治理入口。
  - [x] 验收标准已确认：接口需要认证和 project 归属校验；Python Agent 后续必须通过 Spring Tool Gateway 调工具；真实 MCP 调用留到后续 transport adapter。
  - [x] 依赖和风险已记录：依赖 B04 seed 的 `mcp_endpoints`；B08 不执行真实外部 MCP 工具，不保存明文 secret。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B08 无新增 Flyway 迁移，复用 `mcp_endpoints`、`mcp_health_checks`、`task_logs` 表。
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/src/main/java/com/agentdesk/backend/mcp/**`
    - `backend-spring/src/main/java/com/agentdesk/backend/security/SecurityConfig.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/mcp/McpControllerTest.java`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.mcp.McpControllerTest`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.mcp.McpControllerTest --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.mcp.McpControllerTest --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.mcp.McpControllerTest`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.mcp.McpControllerTest --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.mcp.McpControllerTest --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'`
    - `GET /api/v1/projects/:id/mcp`
    - `POST /api/v1/projects/:id/mcp`
    - `PATCH /api/v1/mcp/:id`
    - `POST /api/v1/mcp/:id/health-check`
    - `GET /api/v1/projects/:id/mcp/tools`
    - `POST /api/v1/projects/:id/mcp/health-check-all`
    - `GET /api/v1/projects/:id/mcp/health-status`
    - `POST /internal/tools/invoke`
    - `GET /api/v1/mcp/:id` with another user's token
  - 测试结果：
    - B08 定向 `McpControllerTest`：通过
    - B05+B06+B07+B08 组合回归：通过
    - 默认 profile `./gradlew clean test`：通过
    - dev profile `SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`：通过
    - `./gradlew bootJar`：通过
    - HTTP 冒烟：MCP 列表、新增、更新、单端点健康检查、批量健康检查、工具注册表、内部工具调用占位审计、跨用户 403 均通过
    - `secret_input` 不出现在响应中，只返回 `secret_ref`；B08 不执行真实外部 MCP 工具
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - 项目级 MCP 子路径补充进入 `SecurityConfig` 认证规则，避免 `tools`、`health-status`、`health-check-all` 旁路认证。
    - `/internal/tools/invoke` 增加 `X-Internal-Token` 校验和 task log 审计占位，真实 transport adapter 后续接入。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B09 - SSE 与 Agent Job

- Planning Agent:
  - [x] 阶段范围已确认：实现消息发送后的 agent job 入队占位、`agent.events:{threadId}` 事件模型、`/threads/:id/stream` SSE、断点续传和 mock worker 输出。
  - [x] 验收标准已确认：前端可以连接 SSE 看到 pending agent 消息转为 completed；暂不接真实 Python Agent。
  - [x] 依赖和风险已记录：依赖 B05 message placeholder 和 B08 task log；B09 先用 Spring 内部 mock worker，后续 B10 再接 Python。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B09 无新增 Flyway 迁移，复用 `messages` 表和内存 SSE event buffer。
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/AgentEvent.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/AgentEventBus.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/AgentJobService.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/WorkspaceController.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/WorkspaceService.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/WorkspaceRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/InMemoryWorkspaceRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/JdbcWorkspaceRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/security/SecurityConfig.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/workspace/WorkspaceControllerTest.java`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --tests com.agentdesk.backend.mcp.McpControllerTest --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --tests com.agentdesk.backend.mcp.McpControllerTest --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --tests com.agentdesk.backend.mcp.McpControllerTest --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --tests com.agentdesk.backend.mcp.McpControllerTest --tests com.agentdesk.backend.skill.SkillControllerTest --tests com.agentdesk.backend.role.RoleControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'`
    - `POST /api/v1/threads/:id/messages`
    - `GET /api/v1/threads/:id/messages?limit=20`
    - `GET /api/v1/threads/:id/stream?last_event_id=0&replay_only=true`
  - 测试结果：
    - B09 定向 `WorkspaceControllerTest`：通过
    - B05+B06+B07+B08+B09 组合回归：通过
    - 默认 profile `./gradlew clean test`：通过
    - dev profile `SPRING_PROFILES_ACTIVE=dev ./gradlew test --rerun-tasks`：通过
    - `./gradlew bootJar`：通过
    - HTTP/SSE 冒烟：发送消息后 mock agent job 生成，pending agent 占位转 completed，SSE 回放包含 `message_completed` 和 Mock Agent 内容
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - Spring MVC async dispatch 二次鉴权会拦截 SSE `asyncDispatch`，已在 `SecurityConfig` 放行 `DispatcherType.ASYNC`，入口请求仍由 `/api/v1/threads/**` 认证保护。
    - 幂等重复发送时 agent placeholder 可能已完成，测试不再要求重复响应仍保持 pending，而改查最终消息状态。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B10 - Python Agent 基础服务

- Planning Agent:
  - [x] 阶段范围已确认：创建 `agent-python` FastAPI 服务骨架、内部健康接口、Redis worker skeleton、LangGraph 依赖占位和 Spring 调用契约。
  - [x] 验收标准已确认：Python 不直接写核心业务表；只提供 internal API、worker skeleton 和本地测试，真实编排后续阶段扩展。
  - [x] 依赖和风险已记录：依赖 B09 的消息/job/SSE 事件模型；B10 不替换 Spring mock worker。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B10 无数据库迁移；新增 Python 服务、Compose 服务和 Docker 构建忽略规则。
  - [x] 自测命令已运行
  - 变更文件：
    - `.gitignore`
    - `docker/docker-compose.yml`
    - `agent-python/.dockerignore`
    - `agent-python/Dockerfile`
    - `agent-python/README.md`
    - `agent-python/pyproject.toml`
    - `agent-python/app/config.py`
    - `agent-python/app/gateway/spring_tools.py`
    - `agent-python/app/graph/orchestrator.py`
    - `agent-python/app/graph/router.py`
    - `agent-python/app/graph/state.py`
    - `agent-python/app/job_store.py`
    - `agent-python/app/main.py`
    - `agent-python/app/models.py`
    - `agent-python/app/security.py`
    - `agent-python/app/skills/runner.py`
    - `agent-python/app/worker.py`
    - `agent-python/tests/test_internal_api.py`
  - 自测命令：
    - `cd agent-python && python3 -m venv .venv`
    - `cd agent-python && .venv/bin/python -m pip install --upgrade pip`
    - `cd agent-python && .venv/bin/python -m pip install -e '.[dev]'`
    - `cd agent-python && .venv/bin/python -m pytest`
    - `cd agent-python && .venv/bin/python -m compileall app tests`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `docker compose -f docker/docker-compose.yml config`
    - `cd agent-python && .venv/bin/python -m pytest`
    - `cd agent-python && .venv/bin/python -m compileall app tests`
    - `DOCKER_CONFIG=/tmp/agentdesk-docker-config /Users/yangzhecheng/.docker/cli-plugins/docker-compose -f docker/docker-compose.yml up -d --build agent-python`
    - `curl -sf -H 'X-Internal-Token: local-dev-internal-token' http://localhost:8001/internal/health/workers`
    - `curl -sf -H 'X-Internal-Token: local-dev-internal-token' -H 'Content-Type: application/json' -d '{"job_id":"job-b10-docker","thread_id":"thread-docker","project_id":"project-1","user_id":"user-1","content":"容器验收","rag":{"enabled":false}}' http://localhost:8001/internal/agent/jobs`
  - 测试结果：
    - Python 本地测试：5 passed
    - Python `compileall`：通过
    - Docker Compose 配置：通过，新增 `agent-python` 服务依赖 Redis 健康检查。
    - Docker 容器验收：`agent-python` 构建成功，`/internal/health/workers` 返回 `service=agent-python`、`redis_stream=agent.jobs`，`/internal/agent/jobs` 返回 `status=queued` 和 `agent.events:thread-docker`。
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - Docker Desktop 默认凭据读取进程曾卡住镜像构建；改用空 `DOCKER_CONFIG=/tmp/agentdesk-docker-config` 调用 compose plugin 后构建和容器验收通过。
    - 首次容器健康检查曾在 Uvicorn 完全就绪前触发，等待服务启动后复测通过。
    - 新增 `.dockerignore`，将构建上下文从包含本地虚拟环境的 13MB 降到约 4KB。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B11 - RAG 上传索引

- Planning Agent:
  - [x] 阶段范围已确认：实现前端文件上传入口对应的后端 RAG 上传索引基础能力，包括 Spring 上传 API、MinIO 原文存储、`rag_documents` / `rag_index_jobs` 状态流转、Redis `rag.index.jobs` 事件和 Python 索引 worker skeleton。
  - [x] 验收标准已确认：浏览器仍只访问 Spring `/api/v1/threads/:id/rag/uploads`；Python 不直接写核心业务表；B11 只完成上传入队和索引任务骨架，不要求真实 embedding/Milvus 写入。
  - [x] 依赖和风险已记录：依赖 B02 MinIO/Redis、B03 认证、B05 thread 权限和 B10 Python 服务；Milvus/Embedding Gateway 的真实写入在 B12 前后继续完善。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B11 复用 V1 已存在 `rag_documents`、`rag_index_jobs`、`rag_chunks` 表；新增 MinIO secret-key 配置和 Python RAG index stream 配置。
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/build.gradle`
    - `backend-spring/src/main/java/com/agentdesk/backend/config/InfrastructureProperties.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/WorkspaceService.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/rag/**`
    - `backend-spring/src/main/resources/application.yml`
    - `backend-spring/src/main/resources/application-dev.yml`
    - `backend-spring/src/test/java/com/agentdesk/backend/config/InfrastructurePropertiesTest.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/rag/RagControllerTest.java`
    - `agent-python/app/config.py`
    - `agent-python/app/main.py`
    - `agent-python/app/models.py`
    - `agent-python/app/worker.py`
    - `agent-python/app/rag/indexer.py`
    - `agent-python/tests/test_internal_api.py`
    - `agent-python/README.md`
    - `docker/docker-compose.yml`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.rag.RagControllerTest --tests com.agentdesk.backend.config.InfrastructurePropertiesTest`
    - `cd agent-python && .venv/bin/python -m pytest`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.rag.RagControllerTest --tests com.agentdesk.backend.config.InfrastructurePropertiesTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.rag.RagControllerTest --rerun-tasks`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.rag.RagControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd agent-python && .venv/bin/python -m pytest`
    - `cd agent-python && .venv/bin/python -m compileall app tests`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'`
    - `POST /api/v1/threads/:id/rag/uploads` multipart HTTP 冒烟
    - `GET /api/v1/threads/:id/rag/documents?limit=5` HTTP 冒烟
    - `redis-cli XLEN rag.index.jobs`
  - 测试结果：
    - Spring B11 定向测试：通过
    - Spring dev profile RAG 测试：通过，MinIO 返回 `s3://agent-desk-dev/...`，PostgreSQL 写入 `rag_documents` 和 `rag_index_jobs`
    - Spring dev 组合回归：`RagControllerTest` + `WorkspaceControllerTest` 通过
    - Spring 默认 profile `./gradlew clean test`：通过
    - Spring `./gradlew bootJar`：通过
    - Python Agent 测试：6 passed，含 RAG index job contract
    - HTTP 冒烟：上传 `agentdesk-b11-smoke.md` 返回 `document.status=uploaded`、`job.status=queued`、`stream=rag.index.jobs`，列表接口返回已上传文档，Redis `rag.index.jobs` 长度为 1
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - dev profile 下真实 MinIO 返回 `s3://`，测试断言从仅允许 `memory://` 调整为允许 `memory://` 或 `s3://`。
    - 默认全量回归曾与 dev Gradle 回归并行执行导致同一 `build/` 目录竞争，改为顺序执行后通过；后续同一 Gradle 工程测试不要并行跑。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B12 - RAG 检索回答

- Planning Agent:
  - [x] 阶段范围已确认：实现消息发送时的 RAG 检索链路骨架，包括 query embedding 占位、按 `project_id/thread_id` 范围检索、召回片段注入 agent prompt、SSE `rag_retrieval` 事件和回答上下文记录。
  - [x] 验收标准已确认：默认 `scope=thread`，不得跨项目召回；未索引或无召回时消息流程保持可用；真实 Milvus/Embedding 可先用接口边界和可替换 adapter 占位。
  - [x] 依赖和风险已记录：依赖 B09 SSE、B10 Python Agent skeleton 和 B11 上传索引；真实向量库落地时需补 Milvus collection 初始化和 embedding key 的 `secret_ref` 管理。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B12 无新增迁移，复用 `rag_documents` 与 SSE event buffer；真实 `rag_chunks`/Milvus 检索后续接 adapter。
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/src/main/java/com/agentdesk/backend/rag/RagRetrievalService.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/rag/RagRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/AgentJobService.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/WorkspaceService.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/rag/RagControllerTest.java`
    - `agent-python/app/models.py`
    - `agent-python/app/rag/retriever.py`
    - `agent-python/tests/test_internal_api.py`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.rag.RagControllerTest`
    - `cd agent-python && .venv/bin/python -m pytest`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.rag.RagControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.rag.RagControllerTest --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd agent-python && .venv/bin/python -m pytest`
    - `cd agent-python && .venv/bin/python -m compileall app tests`
  - 测试结果：
    - Spring B12 RAG 定向测试：通过，上传后发送消息会在 SSE replay 中出现 `rag_retrieval`、文档来源和 RAG 回答注入内容。
    - Spring dev profile 组合回归：RAG + Workspace 通过。
    - Spring 默认 profile `./gradlew clean test`：通过。
    - Spring `./gradlew bootJar`：通过。
    - Python Agent 测试：7 passed，含 thread scope retrieval filter 计划。
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - MockMvc SSE 内容对中文会出现编码转义展示，测试断言改用稳定 ASCII 标记 `RAG` 和文件名。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B13 - 多 Agent 编排

- Planning Agent:
  - [x] 阶段范围已确认：在现有 Role、Skill、MCP、RAG 和 SSE 基础上增加多 Agent 编排骨架，包括角色路由、handoff 事件、Skill Runner 调用契约和上下文压缩入口占位。
  - [x] 验收标准已确认：消息流不能破坏 B09/B12；handoff 必须产生可回放 SSE 事件；Python 继续不直接写核心业务表；工具调用仍经过 Spring Tool Gateway。
  - [x] 依赖和风险已记录：依赖 B06 角色配置、B07 Skill、B08 MCP Gateway、B09 SSE、B10 Python Agent 和 B12 RAG 检索；真实多模型调用后续需要密钥 `secret_ref` 和运行日志收口。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B13 无新增迁移，复用 SSE event buffer、thread role metadata 和现有消息表。
  - [x] 自测命令已运行
  - 变更文件：
    - `AGENTS.md`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/AgentOrchestrationService.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/workspace/AgentJobService.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/workspace/WorkspaceControllerTest.java`
    - `agent-python/app/models.py`
    - `agent-python/app/graph/planner.py`
    - `agent-python/tests/test_internal_api.py`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd agent-python && .venv/bin/python -m pytest`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.workspace.WorkspaceControllerTest --tests com.agentdesk.backend.rag.RagControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && ./gradlew bootJar`
    - `cd agent-python && .venv/bin/python -m pytest`
    - `cd agent-python && .venv/bin/python -m compileall app tests`
  - 测试结果：
    - Spring Workspace 定向测试：通过，发送登录/token 相关消息后 SSE replay 包含 `agent_selected`、`agent_handoff`、`skill_run_planned` 和 `python-skill-runner`。
    - Spring dev profile 组合回归：Workspace + RAG 通过。
    - Spring 默认 profile `./gradlew clean test`：通过。
    - Spring `./gradlew bootJar`：通过。
    - Python Agent 测试：8 passed，含 handoff、skill、RAG snippet 编排计划。
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - 测试过程中再次触发同一 Gradle 工程并行写 `build/` 的假失败；已按顺序重跑通过，并把禁止并行 Gradle 构建规则补进 `AGENTS.md`。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B14 - 系统设置能力

- Planning Agent:
  - [x] 阶段范围已确认：围绕 `/settings` 页面补后端系统设置能力，包括任务队列视图、运行日志查询、上下文压缩配置、备份配置和工具授权摘要。
  - [x] 验收标准已确认：只暴露 Spring `/api/v1/**` 给浏览器；不泄露真实密钥；设置修改必须按项目鉴权；已有消息、RAG、MCP、Skill 接口不能回归。
  - [x] 依赖和风险已记录：依赖 B03 用户偏好、B08 task_logs/MCP 审计、B09/B13 agent events；真实备份任务和密钥托管先做接口边界和可替换占位。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B14 无新增迁移，复用 `projects.settings` 和 `task_logs`；新增 default/dev 双 repository 实现。
  - [x] 自测命令已运行
  - 变更文件：
    - `backend-spring/src/main/java/com/agentdesk/backend/settings/SettingsController.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/settings/SettingsService.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/settings/SettingsDtos.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/settings/SettingsRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/settings/InMemorySettingsRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/settings/JdbcSettingsRepository.java`
    - `backend-spring/src/main/java/com/agentdesk/backend/security/SecurityConfig.java`
    - `backend-spring/src/test/java/com/agentdesk/backend/settings/SettingsControllerTest.java`
  - 自测命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.settings.SettingsControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.settings.SettingsControllerTest --rerun-tasks`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew test --tests com.agentdesk.backend.settings.SettingsControllerTest`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.settings.SettingsControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew clean test`
    - `cd backend-spring && SPRING_PROFILES_ACTIVE=dev ./gradlew test --tests com.agentdesk.backend.settings.SettingsControllerTest --tests com.agentdesk.backend.mcp.McpControllerTest --rerun-tasks`
    - `cd backend-spring && ./gradlew bootJar`
  - 测试结果：
    - Settings 默认 profile 定向测试：通过，覆盖未认证、读取概览、保存上下文压缩/备份/工具授权、运行日志列表、跨用户 403。
    - Settings dev profile 定向测试：通过，验证 PostgreSQL `projects.settings` 读写和 `task_logs` 查询。
    - 默认 profile `./gradlew clean test`：通过。
    - dev profile Settings + MCP 组合回归：通过。
    - `./gradlew bootJar`：通过。
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - PostgreSQL 对空参数 `:type IS NULL` 的类型推断导致 task log 查询 500；改为动态 SQL，仅在过滤条件存在时拼接 `type/level` 参数。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B15 - 前端 API 接入

- Planning Agent:
  - [x] 阶段范围已确认：新增前端 API adapter 与服务层，分页面替换 Pinia mock 读取/写入路径，优先接 bootstrap、thread/message、SSE、RAG 上传、settings overview。
  - [x] 验收标准已确认：默认无后端时仍可使用 mock；配置后端地址后走 Spring API；输入框下方文件按钮作为 RAG 上传索引入口；现有 4 个页面 E2E 不回归。
  - [x] 依赖和风险已记录：依赖 B04-B14 后端接口；前端切换必须保留 query/localStorage/default active thread 规则，避免一次性大改所有 store。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：B15 无数据库迁移；新增前端 API adapter、SSE client 和可选后端模式。
  - [x] 自测命令已运行
  - 变更文件：
    - `src/api/session.js`
    - `src/api/http.js`
    - `src/api/bootstrap.js`
    - `src/api/threads.js`
    - `src/api/rag.js`
    - `src/api/settings.js`
    - `src/services/sseClient.js`
    - `src/stores/thread.js`
    - `src/views/MainWorkspace.vue`
    - `src/views/SystemSettings.vue`
    - `__tests__/thread.test.js`
  - 自测命令：
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:unit`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run build`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:unit`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run build`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:e2e`
  - 测试结果：
    - Vitest：4 files / 7 tests passed，新增 backend bootstrap hydrate 映射测试。
    - Vite build：通过。
    - Playwright：101 passed，确认默认 mock 模式、工作台文件按钮旧行为和 `/settings` 平台入口不回归。
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - B15 采用后端可选模式：未配置 `VITE_AGENT_DESK_API_BASE_URL`、`agentDesk.api.accessToken`、`agentDesk.api.projectId` 时不触发后端请求，保持现有 mock 行为。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段

### B16 - 观测、安全与部署

- Planning Agent:
  - [x] 阶段范围已确认：生产前收口观测、安全与部署，包括 metrics/health 扩展、敏感配置校验、Docker/Compose 生产化建议、CI 验证命令和部署文档。
  - [x] 验收标准已确认：不写真实密钥；默认开发体验不破坏；后端、Python、前端测试命令形成可复用发布检查；Git 推送后工作区保持干净。
  - [x] 依赖和风险已记录：依赖 B01-B15；真实生产部署仍需外部域名、证书、密钥托管和 CI 环境变量，当前阶段先做本仓库可验证闭环。
- Development Agent:
  - [x] 代码实现完成
  - [x] 数据库迁移/配置更新完成：无新增数据库迁移；补充前端、Spring 后端、Python Agent 环境样例和 CI/部署文档。
  - [x] 自测命令已运行
  - 变更文件：
    - `docs/ci-workflow.example.yml`
    - `.env.example`
    - `backend-spring/.env.example`
    - `agent-python/.env.example`
    - `docs/DEPLOYMENT.md`
    - `README.md`
    - `docker/README.md`
    - `BACKEND-DEVELOPMENT-LOG.md`
  - 自测命令：
    - `git diff --check`
    - `docker compose -f docker/docker-compose.yml config >/tmp/agentdesk-compose-config.out`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:unit`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run build`
    - `cd agent-python && .venv/bin/python -m pytest && .venv/bin/python -m compileall app tests`
- Testing Agent:
  - [x] 单元测试通过
  - [x] 集成测试通过
  - [x] 回归测试通过
  - 测试命令：
    - `cd backend-spring && ./gradlew clean test && ./gradlew bootJar`
    - `PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:e2e`
  - 测试结果：
    - Compose 配置解析通过。
    - GitHub Actions workflow 已作为 `docs/ci-workflow.example.yml` 示例保留；当前 OAuth 凭据缺少 `workflow` scope，不能直接推送 `.github/workflows/ci.yml`。
    - Vitest：4 files / 7 tests passed。
    - Vite build：通过。
    - Spring 后端：`clean test` 和 `bootJar` 均通过。
    - Python Agent：8 passed，`compileall app tests` 通过。
    - Playwright：101 passed。
- Bugs:
  - [x] 无阻塞 bug
  - 修复记录：
    - 无。
- Gate:
  - [x] Dev Done
  - [x] Test Done
  - [x] Planning Agent 已批准进入下一阶段：当前已无预设 B17，下一阶段需按新需求规划后追加。
