import asyncio
import json
from typing import Any, Dict

from redis.asyncio import Redis

from app.config import settings


class AgentWorker:
    def __init__(self, redis: Redis) -> None:
        self.redis = redis

    async def enqueue(self, job: Dict[str, Any]) -> str:
        return await self.redis.xadd(settings.agent_jobs_stream, {"payload": json.dumps(job, ensure_ascii=False)})

    async def run_once(self) -> None:
        # B10 skeleton: prove Redis Streams wiring without executing LangGraph.
        await self.redis.xgroup_create(settings.agent_jobs_stream, "agent-workers", id="0", mkstream=True)


async def main() -> None:
    redis = Redis.from_url(settings.redis_url, decode_responses=True)
    worker = AgentWorker(redis)
    await worker.run_once()
    await redis.aclose()


if __name__ == "__main__":
    asyncio.run(main())

