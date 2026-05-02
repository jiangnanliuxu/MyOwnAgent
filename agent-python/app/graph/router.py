from app.graph.state import AgentState


def choose_role(state: AgentState) -> str:
    content = state.get("content", "")
    if "测试" in content or "test" in content.lower():
        return "test"
    if "登录" in content or "auth" in content.lower():
        return "auth"
    if "路由" in content or "route" in content.lower():
        return "route"
    return "primary"

