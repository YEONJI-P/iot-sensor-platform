FROM python:3.11-slim

# uv 설치 (공식 이미지에서 바이너리 복사)
COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

WORKDIR /app

# 의존성 먼저 설치 (레이어 캐시 활용)
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

COPY app ./app

# 포트는 PORT env로 주입(기본 23200). 하드코딩하지 않는다 — CLAUDE.md 포트 규칙.
ENV PORT=23200
EXPOSE 23200
# shell form이라야 ${PORT}가 확장된다
CMD uv run uvicorn app.main:app --host 0.0.0.0 --port ${PORT}
