# Deployment And Release Checks

This project is still a local-first prototype, but B16 keeps the release path
explicit so later production work has a stable checklist.

## Services

- Frontend: Vite static build from `npm run build`.
- Spring backend: `backend-spring/build/libs/*.jar`.
- Python Agent: FastAPI app in `agent-python`, normally run by Uvicorn.
- Local infrastructure: PostgreSQL, Redis, MinIO, optional Milvus.

Browsers should only call Spring `/api/v1/**`. Python, Redis, MinIO, Milvus and
MCP servers stay behind the Spring control plane.

## Required Checks

Run these before pushing a completed stage:

```bash
PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run build
PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:unit
PATH=/Users/yangzhecheng/.nvm/versions/node/v22.22.1/bin:$PATH npm run test:e2e

cd backend-spring
./gradlew clean test
./gradlew bootJar

cd ../agent-python
.venv/bin/python -m pytest
.venv/bin/python -m compileall app tests
```

Do not run multiple Gradle test/build commands for `backend-spring` in parallel.
They share the same `build/` directory and can create false failures.

## CI Workflow

`docs/ci-workflow.example.yml` contains the GitHub Actions workflow draft for
the same checks. Copy it to `.github/workflows/ci.yml` only when the GitHub token
used for pushing has `workflow` scope; otherwise GitHub rejects the push.

## Security Checklist

- Replace `AGENT_DESK_JWT_SECRET` outside local development.
- Store real model, MCP and embedding credentials in a secret manager; database
  rows should contain only `secret_ref` or masked metadata.
- Keep Python Agent internal APIs off the public network.
- Keep Redis, PostgreSQL, MinIO and Milvus private to the deployment network.
- Require Spring `/internal/**` calls to carry an internal token or stronger
  service identity.
- Never commit real API keys, user files, production URLs, or bearer tokens.

## Observability

- Spring exposes `/actuator/health` and `/api/v1/health`.
- Request IDs are carried in `X-Request-Id` and returned as `request_id`.
- Tool invocations and settings-adjacent runtime events should be represented in
  `task_logs`.
- Agent stream events use `/api/v1/threads/:id/stream` and should remain
  replayable with `last_event_id`.

## Local Release Smoke

```bash
docker compose -f docker/docker-compose.yml up -d postgres redis minio minio-init agent-python
cd backend-spring
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun --args='--server.port=18080'
curl -s http://localhost:18080/api/v1/health
curl -s -H 'X-Internal-Token: local-dev-internal-token' http://localhost:8001/internal/health/workers
```
