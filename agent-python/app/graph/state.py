from typing import Any, Dict, List, TypedDict


class AgentState(TypedDict, total=False):
    job_id: str
    thread_id: str
    project_id: str
    content: str
    selected_role: str
    retrieved_context: List[Dict[str, Any]]
    result: str

