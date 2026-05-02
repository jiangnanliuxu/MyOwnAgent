from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    internal_token: str = os.getenv("AGENT_INTERNAL_TOKEN", "local-dev-internal-token")
    redis_url: str = os.getenv("AGENT_REDIS_URL", "redis://localhost:6379/0")
    agent_jobs_stream: str = os.getenv("AGENT_JOBS_STREAM", "agent.jobs")
    rag_index_jobs_stream: str = os.getenv("RAG_INDEX_JOBS_STREAM", "rag.index.jobs")
    spring_tool_gateway_url: str = os.getenv(
        "SPRING_TOOL_GATEWAY_URL",
        "http://localhost:18080/internal/tools/invoke",
    )


settings = Settings()
