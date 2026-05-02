from typing import Any, Dict, Optional

import httpx

from app.config import settings


class SpringToolGateway:
    def __init__(self, base_url: str = settings.spring_tool_gateway_url, token: str = settings.internal_token) -> None:
        self.base_url = base_url
        self.token = token

    async def invoke(
        self,
        project_id: str,
        tool_name: str,
        arguments: Dict[str, Any],
        endpoint_id: Optional[str] = None,
        endpoint_client_key: Optional[str] = None,
    ) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "project_id": project_id,
            "tool_name": tool_name,
            "arguments": arguments,
        }
        if endpoint_id:
            payload["endpoint_id"] = endpoint_id
        if endpoint_client_key:
            payload["endpoint_client_key"] = endpoint_client_key
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(self.base_url, json=payload, headers={"X-Internal-Token": self.token})
            response.raise_for_status()
            return response.json()

