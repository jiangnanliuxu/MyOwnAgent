from typing import Dict, List

from app.models import RagRetrievalQuery


def plan_retrieval(query: RagRetrievalQuery) -> Dict[str, object]:
    filters: List[str] = [f"project_id == '{query.project_id}'"]
    if query.scope == "thread":
        filters.append(f"thread_id == '{query.thread_id}'")
    return {
        "status": "planned",
        "collection": "agent_desk_chunks",
        "top_k": query.top_k,
        "filter": " and ".join(filters),
    }
