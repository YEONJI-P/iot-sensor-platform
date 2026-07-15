"""전역 예외 핸들러.

HTTPException은 FastAPI 기본 핸들러가 처리하므로 등록하지 않는다.
예측하지 못한 예외(provider 내부 오류 등)만 표준 500으로 변환한다.
"""

import logging

from fastapi import Request
from fastapi.responses import JSONResponse

logger = logging.getLogger(__name__)


async def handle_unexpected(request: Request, exc: Exception) -> JSONResponse:
    """모든 미처리 예외를 표준 500 응답으로 변환한다."""
    logger.exception("unexpected error on %s %s", request.method, request.url.path)
    return JSONResponse(
        status_code=500,
        content={
            "error": "internal",
            "detail": str(exc),
            "path": request.url.path,
        },
    )
