from fastapi import Header, HTTPException, status

from app.config import settings


def require_internal_token(
    authorization: str = Header(default=""),
    x_internal_token: str = Header(default=""),
) -> None:
    bearer_prefix = "Bearer "
    token = ""
    if authorization.startswith(bearer_prefix):
        token = authorization[len(bearer_prefix) :]
    elif x_internal_token:
        token = x_internal_token
    if token != settings.internal_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Internal token is required.",
        )

