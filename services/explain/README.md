# explain 서비스 (이상 설명·진단)

센서 이상에 근거와 권고를 붙이고, 데이터 수신 끊김의 원인을 진단하는 Python/FastAPI 서비스. 탐지 자체는 Spring이 규칙으로 수행하고, 이 서비스는 설명과 권고만 LLM으로 생성한다. Spring과는 HTTP 요청-응답으로 연동한다(메시지 버스 없음).

Spring backend와 HTTP로 연동된 상태이며, 기본 provider는 키 없이 동작하는 echo 스텁입니다. Gemini provider는 `google-genai` SDK와 안정 모델 `gemini-3.1-flash-lite`의 실제 호출을 확인했습니다.

## 구조

```
explain/
├── pyproject.toml          uv 프로젝트 설정
├── .python-version         3.11
├── .env.example
├── Dockerfile
├── app/
│   ├── main.py             FastAPI 앱, /health, 미들웨어, 예외 핸들러, 라우터 등록
│   ├── dependencies.py     Settings + provider 팩토리 (Depends 주입)
│   ├── schemas.py          요청/응답 스키마 (camelCase <-> snake_case)
│   ├── errors.py           전역 예외 -> 500
│   ├── middleware.py       X-Process-Time
│   ├── providers/          LLM 추상화
│   │   ├── base.py         LLMProvider 인터페이스
│   │   ├── echo.py         키 없이 동작하는 스텁 (기본)
│   │   └── gemini.py       Gemini 구현 (google 패키지 지연 import)
│   └── routers/
│       ├── anomaly.py      POST /explain/anomaly   (이상 근거·권고)
│       └── freshness.py    POST /explain/freshness (끊김 원인 진단)
└── tests/
    └── test_health.py      echo provider 기준 스모크 테스트
```

## 실행 (uv)

```bash
cd services/explain

# 의존성 설치 (uv.lock 생성)
uv sync

# 개발 서버
uv run uvicorn app.main:app --reload --port 23200

# 테스트 (echo provider, 키 불필요)
uv run pytest
```

Swagger UI: `http://localhost:23200/docs`

## provider 전환

`.env`(또는 환경변수)로 provider를 고른다. 기본은 echo.

```bash
EXPLAIN_PROVIDER=echo            # 키 없이 동작하는 스텁 (기본)
# 또는
EXPLAIN_PROVIDER=gemini
GEMINI_API_KEY=...                    # Google AI Studio 발급
MODEL_NAME=gemini-3.1-flash-lite      # 배포에서 검증한 안정 모델
```

## 엔드포인트

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/health` | 헬스 체크 |
| POST | `/explain/anomaly` | 이상 신호에 근거(규칙)와 권고(LLM)를 붙여 반환 |
| POST | `/explain/freshness` | 끊김 신호에 원인 추정(규칙)과 리포트(LLM)를 붙여 반환 |

## 남은 작업

- 장치별 센서값 라인 차트, 알림 현황 시각화
- SSE(`/dashboard/stream`) 구독으로 수신, 알림 이벤트를 실시간 반영
