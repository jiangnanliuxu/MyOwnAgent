# Agent Desk B02 Development Infrastructure

This compose stack is for local development only. It starts PostgreSQL 16,
Redis 7, and MinIO with persistent Docker volumes and predictable dev
credentials. The `minio-init` service creates the `agent-desk-dev` bucket.

## Start

```bash
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
```

To start only the required long-running services:

```bash
docker compose -f docker/docker-compose.yml up -d postgres redis minio
```

## Default endpoints

| Service | Endpoint | Credentials |
| --- | --- | --- |
| PostgreSQL | `localhost:5432/agentdesk` | `agentdesk` / `agentdesk_dev_password` |
| Redis | `localhost:6379` | no password in local dev |
| MinIO API | `http://localhost:9000` | `agentdesk` / `agentdesk_dev_minio_password` |
| MinIO Console | `http://localhost:9001` | `agentdesk` / `agentdesk_dev_minio_password` |

## Stop

```bash
docker compose -f docker/docker-compose.yml down
```

To remove local data as well:

```bash
docker compose -f docker/docker-compose.yml down -v
```

## Milvus

Milvus is intentionally not part of the B02 required compose stack. RAG tables
store document and chunk metadata in PostgreSQL, while actual vector storage can
be wired later through a separate Milvus deployment.

Use the official standalone guide when vector search work starts:

```text
https://milvus.io/docs/install_standalone-docker.md
```

Keep the Spring property `agent-desk.infrastructure.milvus.enabled=false` until
that service is available.
