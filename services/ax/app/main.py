"""FastAPI 애플리케이션 진입점.

라우트 구성:
- GET  /health                : 헬스 체크
- POST /ax/explain-anomaly     : 이상 근거·권고 생성 (spine① 이상)
- POST /ax/diagnose-freshness  : 데이터 끊김 원인 진단 (spine② 데이터 안 옴)
- GET  /docs                   : Swagger UI

이상 탐지 자체는 Spring이 규칙으로 수행하고, 이 서비스는 그 결과에
근거·권고·원인 설명을 붙이는 역할만 한다. 탐지=규칙, 설명=LLM.
"""

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .errors import handle_unexpected
from .middleware import add_process_time
from .routers import anomaly, freshness

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s — %(message)s",
)

app = FastAPI(
    title="IoT Sensor Platform — AX",
    description="센서 이상 근거·권고 생성 및 데이터 끊김 원인 진단 서비스",
    version="0.1.0",
)

# CORS — Spring(8080)과 로컬 프론트(5173)만 허용
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://localhost:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.middleware("http")(add_process_time)

# 예측하지 못한 예외를 표준 500으로 변환
app.add_exception_handler(Exception, handle_unexpected)

# 라우터
app.include_router(anomaly.router)
app.include_router(freshness.router)


@app.get("/health", tags=["meta"])
def health() -> dict[str, str]:
    """헬스 체크 — 컨테이너 liveness 용도."""
    return {"status": "ok"}
