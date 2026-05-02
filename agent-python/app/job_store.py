from datetime import datetime, timezone
from typing import Dict, Optional

from app.models import AgentJobRequest, AgentJobStatus


class InMemoryJobStore:
    def __init__(self) -> None:
        self._jobs: Dict[str, AgentJobStatus] = {}
        self._payloads: Dict[str, AgentJobRequest] = {}

    def enqueue(self, request: AgentJobRequest) -> AgentJobStatus:
        status = AgentJobStatus(
            job_id=request.job_id,
            status="queued",
            stream=f"agent.events:{request.thread_id}",
            queued_at=datetime.now(timezone.utc),
        )
        self._jobs[request.job_id] = status
        self._payloads[request.job_id] = request
        return status

    def get(self, job_id: str) -> Optional[AgentJobStatus]:
        return self._jobs.get(job_id)


job_store = InMemoryJobStore()

