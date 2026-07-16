# AX 서비스 (AI 분석)

센서 이상에 근거와 권고를 붙이고, 데이터 수신 끊김의 원인을 진단하는 Python/FastAPI 서비스. 탐지 자체는 Spring이 규칙으로 수행하고, 이 서비스는 설명과 권고만 LLM으로 생성한다. Spring과는 HTTP 요청-응답으로 연동한다(메시지 버스 없음).

현재 상태는 골격(스켈레톤)이다. 기본 provider는 키 없이 동작하는 echo 스텁이며, Gemini 연동은 provider 교체로 붙인다.

## 구조

```
ax/
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
│       ├── anomaly.py      POST /ax/explain-anomaly   (이상 근거·권고)
│       └── freshness.py    POST /ax/diagnose-freshness (끊김 원인 진단)
└── tests/
    └── test_health.py      echo provider 기준 스모크 테스트
```

## 실행 (uv)

```bash
cd ax

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
AX_PROVIDER=echo            # 키 없이 동작하는 스텁 (기본)
# 또는
AX_PROVIDER=gemini
GEMINI_API_KEY=...          # Google AI Studio 발급
MODEL_NAME=gemini-2.0-flash # 실제 사용 가능 모델은 AI Studio에서 확인
```

## 엔드포인트

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/health` | 헬스 체크 |
| POST | `/ax/explain-anomaly` | 이상 신호에 근거(규칙)와 권고(LLM)를 붙여 반환 |
| POST | `/ax/diagnose-freshness` | 끊김 신호에 원인 추정(규칙)과 리포트(LLM)를 붙여 반환 |

## 남은 작업

- Gemini provider 실호출 검증 (모델명·SDK 버전 확정, `uv add`로 설치)
- Spring 측 AX 클라이언트(HTTP) 연동, Alert의 evidence/recommendation 채우기
- docker-compose에 ax 서비스 추가
