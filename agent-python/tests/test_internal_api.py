from fastapi.testclient import TestClient

from app.graph.orchestrator import run_skeleton_graph
from app.main import app
from app.models import RagIndexJob
from app.rag.indexer import plan_index_job


client = TestClient(app)
headers = {"Authorization": "Bearer local-dev-internal-token"}


def test_internal_health_requires_token() -> None:
    response = client.get("/internal/health/workers")
    assert response.status_code == 401


def test_internal_health_reports_worker_contract() -> None:
    response = client.get("/internal/health/workers", headers=headers)
    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "agent-python"
    assert body["redis_stream"] == "agent.jobs"
    assert body["rag_index_stream"] == "rag.index.jobs"
    assert body["workers"][0]["mode"] == "b10-skeleton"
    assert body["workers"][1]["mode"] == "b11-skeleton"


def test_agent_job_lifecycle() -> None:
    payload = {
        "job_id": "job-b10-1",
        "thread_client_key": "session-review",
        "thread_id": "thread-1",
        "project_id": "project-1",
        "user_id": "user-1",
        "content": "帮我分析登录流程",
        "context": {"attach_folder": "src/auth"},
        "rag": {"enabled": False},
        "role_configs": {},
        "thread_history": [],
        "preferences": {"language": "zh-CN"},
    }
    created = client.post("/internal/agent/jobs", json=payload, headers=headers)
    assert created.status_code == 202
    assert created.json()["status"] == "queued"
    assert created.json()["stream"] == "agent.events:thread-1"

    fetched = client.get("/internal/agent/jobs/job-b10-1", headers=headers)
    assert fetched.status_code == 200
    assert fetched.json()["job_id"] == "job-b10-1"


def test_skill_run_placeholder() -> None:
    response = client.post(
        "/internal/skills/run",
        json={"skill_id": "frontend-design", "thread_id": "thread-1", "project_id": "project-1"},
        headers={"X-Internal-Token": "local-dev-internal-token"},
    )
    assert response.status_code == 202
    assert response.json()["status"] == "accepted"


def test_skeleton_graph_selects_role_from_content() -> None:
    result = run_skeleton_graph(
        {
            "job_id": "job-b10-graph",
            "thread_id": "thread-1",
            "project_id": "project-1",
            "content": "请测试登录流程",
        }
    )
    assert result["selected_role"] == "test"
    assert "B10 skeleton" in result["result"]


def test_rag_index_job_contract_is_plannable() -> None:
    plan = plan_index_job(
        RagIndexJob(
            job_id="rag-job-1",
            document_id="doc-1",
            project_id="project-1",
            thread_id="thread-1",
            storage_uri="s3://agent-desk-dev/rag/project/thread/doc/readme.md",
            source_name="readme.md",
            mime_type="text/markdown",
            size_bytes=128,
            sha256="a" * 64,
        )
    )
    assert plan["status"] == "accepted"
    assert plan["next_step"] == "parse_chunk_embed_upsert"
