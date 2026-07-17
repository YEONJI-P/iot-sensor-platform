FROM python:3.11-slim

# uv 설치 (공식 이미지에서 바이너리 복사)
COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

WORKDIR /app

# 의존성 먼저 설치 (레이어 캐시 활용)
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

COPY app ./app

# 이미지 기본값 = 배포 계약(8000). 앱 코드 기본값 23200 은 `uv run uvicorn` 로컬 개발용이며,
# 컨테이너에는 적용하지 않는다. 포트는 여전히 PORT env 로 재정의 가능하다 — 하드코딩하지 않는다.
ENV PORT=8000
# 문서용 메타데이터 — 위 PORT 기본값과 같은 값을 유지한다(런타임 매핑은 compose/배포 측 소관).
EXPOSE 8000
# shell form이라야 ${PORT}가 확장된다
CMD uv run uvicorn app.main:app --host 0.0.0.0 --port ${PORT}
