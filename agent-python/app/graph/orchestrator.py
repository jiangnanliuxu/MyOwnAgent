from app.graph.router import choose_role
from app.graph.state import AgentState


def run_skeleton_graph(state: AgentState) -> AgentState:
    role = choose_role(state)
    return {
        **state,
        "selected_role": role,
        "result": f"B10 skeleton selected {role}; real LangGraph execution is implemented later.",
    }

