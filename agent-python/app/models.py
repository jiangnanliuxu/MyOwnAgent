from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


class RagOptions(BaseModel):
    enabled: bool = False
    top_k: int = Field(default=6, ge=1, le=20)
    scope: str = "thread"


class AgentJobRequest(BaseModel):
    job_id: str
    thread_client_key: Optional[str] = None
    thread_id: str
    project_id: str
    user_id: Optional[str] = None
    content: str
    context: Dict[str, Any] = Field(default_factory=dict)
    rag: RagOptions = Field(default_factory=RagOptions)
    role_configs: Dict[str, Any] = Field(default_factory=dict)
    thread_history: List[Dict[str, Any]] = Field(default_factory=list)
    preferences: Dict[str, Any] = Field(default_factory=dict)


class AgentJobStatus(BaseModel):
    job_id: str
    status: str
    stream: str
    queued_at: datetime


class SkillRunRequest(BaseModel):
    skill_id: str
    thread_id: str
    project_id: str
    input: Dict[str, Any] = Field(default_factory=dict)
    mounts: List[str] = Field(default_factory=list)


class SkillRunResponse(BaseModel):
    status: str = "accepted"
    run_id: str
    message: str


class WorkerHealth(BaseModel):
    status: str = "ok"
    service: str = "agent-python"
    redis_stream: str
    rag_index_stream: str = "rag.index.jobs"
    workers: List[Dict[str, Any]]
    checked_at: datetime = Field(default_factory=now_utc)


class RagIndexJob(BaseModel):
    job_id: str
    document_id: str
    project_id: str
    thread_id: str
    folder_id: Optional[str] = None
    storage_uri: str
    source_name: str
    source_path: Optional[str] = None
    mime_type: str
    size_bytes: int = Field(ge=0)
    sha256: str
    scope: str = "thread"
    milvus_collection: str = "agent_desk_chunks"
