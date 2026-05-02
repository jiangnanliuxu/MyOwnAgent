from typing import Dict

from app.models import AgentOrchestrationRequest


def plan_orchestration(request: AgentOrchestrationRequest) -> Dict[str, object]:
    selected = _select_role(request.content)
    if request.role_keys and selected not in request.role_keys:
        selected = request.focus_role_key
    return {
        "job_id": request.job_id,
        "thread_id": request.thread_id,
        "from_role_key": request.focus_role_key,
        "to_role_key": selected,
        "handoff_required": selected != request.focus_role_key,
        "skill_ids": request.skill_ids,
        "rag_snippet_count": request.rag_snippet_count,
    }


def _select_role(content: str) -> str:
    normalized = content.lower()
    if "测试" in normalized or "test" in normalized:
        return "test"
    if "路由" in normalized or "route" in normalized:
        return "route"
    if "登录" in normalized or "auth" in normalized or "token" in normalized:
        return "auth"
    if "审查" in normalized or "review" in normalized:
        return "review"
    return "primary"
