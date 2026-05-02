from fastapi import Depends, FastAPI, HTTPException, status

from app.config import settings
from app.job_store import job_store
from app.models import AgentJobRequest, AgentJobStatus, SkillRunRequest, SkillRunResponse, WorkerHealth
from app.security import require_internal_token
from app.skills.runner import run_skill_skeleton

app = FastAPI(title="Agent Python", version="0.1.0")


@app.get("/internal/health/workers", response_model=WorkerHealth)
def workers_health(_: None = Depends(require_internal_token)) -> WorkerHealth:
    return WorkerHealth(
        redis_stream=settings.agent_jobs_stream,
        rag_index_stream=settings.rag_index_jobs_stream,
        workers=[
            {
                "name": "agent-worker-skeleton",
                "status": "idle",
                "mode": "b10-skeleton",
            },
            {
                "name": "rag-index-worker-skeleton",
                "status": "idle",
                "mode": "b11-skeleton",
            }
        ],
    )


@app.post("/internal/agent/jobs", response_model=AgentJobStatus, status_code=status.HTTP_202_ACCEPTED)
def submit_agent_job(
    request: AgentJobRequest,
    _: None = Depends(require_internal_token),
) -> AgentJobStatus:
    return job_store.enqueue(request)


@app.get("/internal/agent/jobs/{job_id}", response_model=AgentJobStatus)
def get_agent_job(job_id: str, _: None = Depends(require_internal_token)) -> AgentJobStatus:
    job = job_store.get(job_id)
    if job is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Job is not available.")
    return job


@app.post("/internal/skills/run", response_model=SkillRunResponse, status_code=status.HTTP_202_ACCEPTED)
def run_skill(
    request: SkillRunRequest,
    _: None = Depends(require_internal_token),
) -> SkillRunResponse:
    result = run_skill_skeleton(request.skill_id, request.input)
    return SkillRunResponse(
        run_id=f"skill-run-{request.skill_id}-{request.thread_id}",
        message=result["message"],
    )
