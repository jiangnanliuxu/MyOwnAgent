# MyOwnAgent

Agent Desk 是一个面向多 Agent 工作流的前后端原型项目。

当前仓库包含：

- Vue 3 + Pinia 前端工作台，覆盖会话、角色配置、Skill 管理、MCP 管理和系统设置页面。
- Spring Boot 后端骨架，提供统一响应、统一异常、请求 ID 透传、健康检查和基础 CORS 配置。
- 后端架构文档，规划 Spring AI + Python Agent/Skill Worker、MCP Gateway、RAG 上传索引与 Milvus 检索流程。
- 三子 Agent 分段开发流程和开发日志，用于按 Planning / Development / Testing 闸门推进后端实现。

## 技术栈

- Frontend: Vue 3, Pinia, Vite, Playwright, Vitest
- Backend: Spring Boot 3.x, Java 21, Gradle
- Planned AI runtime: Spring AI, Python FastAPI, LangGraph
- Planned data layer: PostgreSQL, Redis Streams, MinIO, Milvus

## 本地运行

前端：

```bash
npm install
npm run dev
```

访问：

```text
http://localhost:4173/
```

后端：

```bash
cd backend-spring
./gradlew bootRun
```

健康检查：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/health
```

### B02 基础设施

B02 的开发基础设施位于 `docker/docker-compose.yml`，包含 PostgreSQL 16、Redis 7 和 MinIO。默认 Spring profile 不连接外部基础设施；需要数据库、Redis 和 Flyway migration 时使用 `dev` profile。
`minio-init` 会创建本地开发 bucket：`agent-desk-dev`。

启动基础设施：

```bash
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
```

使用 `dev` profile 启动后端并执行 Flyway：

```bash
cd backend-spring
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

验证：

```bash
curl http://localhost:8080/actuator/health
docker compose -f docker/docker-compose.yml exec postgres psql -U agentdesk -d agentdesk -c "\\dt"
docker compose -f docker/docker-compose.yml exec redis redis-cli ping
```

默认 dev 连接信息：

| 服务 | 地址 | 账号 |
|------|------|------|
| PostgreSQL | `localhost:5432/agentdesk` | `agentdesk` / `agentdesk_dev_password` |
| Redis | `localhost:6379` | no password in local dev |
| MinIO API | `http://localhost:9000` | `agentdesk` / `agentdesk_dev_minio_password` |
| MinIO Console | `http://localhost:9001` | `agentdesk` / `agentdesk_dev_minio_password` |

Milvus 不在 B02 强制编排范围内。RAG metadata 已落 PostgreSQL，向量库后续按 `docker/README.md` 中的 standalone Milvus 说明单独启动，并在启用前保持 `agent-desk.infrastructure.milvus.enabled=false`。

## 测试

```bash
npm run build
npm run test:unit
npm run test:e2e

cd backend-spring
./gradlew clean test
./gradlew bootJar
```

CI 示例位于 `docs/ci-workflow.example.yml`，按前端、Spring 后端、Python Agent 三个 job 验证。发布前检查和部署注意事项见 `docs/DEPLOYMENT.md`。

## 开发流程

后端按 `BACKEND-DEVELOPMENT-LOG.md` 分段推进。每一阶段必须经过：

1. Planning Agent 确认范围和验收标准。
2. Development Agent 完成实现和修复。
3. Testing Agent 完成测试并放行。

只有 `Dev Done` 和 `Test Done` 都完成后，才能进入下一阶段。

## 安全说明

仓库中的 API Key、服务地址和 MCP 配置均为演示占位数据。真实密钥不得提交到仓库；后端方案要求仅保存 `secret_ref` 或脱敏摘要。
