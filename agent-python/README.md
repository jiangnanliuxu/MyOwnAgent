# Agent Python

Python Agent execution service for Agent Desk.

B10 only provides the internal API, Redis worker skeleton, LangGraph placeholder, and Spring Tool Gateway client. It does not write Spring-owned business tables.

Run locally:

```bash
python3 -m pip install -e ".[dev]"
uvicorn app.main:app --host 0.0.0.0 --port 8001
pytest
```

Docker Compose:

```bash
docker compose -f ../docker/docker-compose.yml up -d redis agent-python
curl -s -H 'X-Internal-Token: local-dev-internal-token' \
  http://localhost:8001/internal/health/workers
```

Important defaults:

- `AGENT_INTERNAL_TOKEN=local-dev-internal-token`
- `AGENT_REDIS_URL=redis://localhost:6379/0`
- `RAG_INDEX_JOBS_STREAM=rag.index.jobs`
- `SPRING_TOOL_GATEWAY_URL=http://localhost:18080/internal/tools/invoke`

Internal API:

- `GET /internal/health/workers`
- `POST /internal/agent/jobs`
- `GET /internal/agent/jobs/{job_id}`
- `POST /internal/skills/run`
