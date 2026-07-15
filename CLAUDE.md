# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 구조 (모노레포)

중립 루트 + 서비스별 도메인 폴더. 루트는 어떤 서비스도 소유하지 않는다.

```
services/
├── backend/     Spring Boot API (build.gradle·gradlew·src, 이 안에서 gradle 실행)
├── ax/          FastAPI AI 분석 서비스 (uv, 자체 Dockerfile)
└── simulator/   Python 시뮬레이터 + seed.sql + data(리플레이용 실측 CSV)
docs/ · docker-compose.yml · .github/ · README.md  ← 루트(프로젝트 공통)
```

gradle 명령은 반드시 `services/backend/`에서 실행한다.

## Commands

### 로컬 개발 환경 시작
```bash
# 의존 서비스 시작 (PostgreSQL + Redis) — 루트에서
docker-compose up -d

# JWT 서명 키 주입 (기본값 없음, 미설정 시 부팅 실패)
export JWT_SECRET=$(head -c 48 /dev/urandom | base64)

# 애플리케이션 실행 (services/backend/)
cd services/backend && ./gradlew bootRun
```

### 빌드 / 테스트 (services/backend/ 에서)
```bash
./gradlew build          # 전체 빌드 (테스트 포함)
./gradlew build -x test  # 테스트 제외 빌드
./gradlew test                                       # 전체 테스트
./gradlew test --tests AuthServiceTest               # 특정 클래스
./gradlew test --tests AuthServiceTest.login_success # 특정 메서드
```
테스트는 인메모리 H2(PostgreSQL 호환 모드)로 동작해 별도 인프라 없이 실행됩니다 (`services/backend/src/test/resources/application.yml`).

## 아키텍처

### 기술 스택
- Java 17, Spring Boot 3.x (Web, Data JPA, Security, Validation)
- PostgreSQL, 주 데이터베이스
- Redis, Refresh Token 저장소
- JWT (jjwt), stateless 인증
- SSE (SseEmitter), 대시보드 실시간 스트림
- Swagger/OpenAPI, `/swagger-ui/index.html` 에서 API 문서 확인
- AX 분석 서비스 (services/ax): Python 3.11, FastAPI, uv. Spring이 HTTP로 호출하는 별도 프로세스

메시지 버스(Kafka)는 제거됨. 소비자가 하나뿐이라 과설계였고, 수신을 동기 처리로 단순화 (설계 근거는 README 설계 메모 참고).

### 도메인 구조
`src/main/java/dev/yeon/iotsensorplatform/` 하위 도메인:

| 패키지 | 역할 |
|--------|------|
| `auth/` | JWT 발급과 검증, Refresh Token, Spring Security 필터 설정 |
| `user/` | 사용자 엔티티, 역할(Role), 상태(UserStatus) |
| `factory/` | 공장(Factory), 구역(Zone), 구역 소속(ZoneUser) 조직 계층 |
| `admin/` | 사용자 승인과 관리, 공장/구역 관리 |
| `device/` | 센서 장치 CRUD (채널=Device. TEMPERATURE, PRESSURE, CURRENT, POWER, ACCELERATION), freshness 스케줄러 |
| `sensordata/` | 센서 데이터 동기 수신, 저장, 임계값 판정, 이상 판정 전략(AnomalyDetector), 실패 적재(failure) |
| `alert/` | 임계값 초과 시 알림 생성과 조회, 근거 보강 스케줄러 |
| `sse/` | 대시보드 실시간 스트림. 커밋 후 이벤트(AFTER_COMMIT) 브로드캐스트, 구독자 접근 범위 필터 |
| `ax/` | AX 서비스 HTTP 클라이언트(AxClient/HttpAxClient), DTO, 설정(ax.base-url/enabled) |
| `global/` | 공통 예외 처리, 접근 제어(AccessControlService), 공유 설정 |

Python 분석 서비스는 `services/ax/` (FastAPI). 엔드포인트 `POST /ax/explain-anomaly`, `POST /ax/diagnose-freshness`. provider 추상화(기본 echo, gemini 선택). 탐지는 Spring 규칙, 설명과 진단만 LLM.

### 데이터 흐름
```
POST /sensor-data
  → SensorDataController
  → SensorDataService.receive() (한 트랜잭션)
      ├─ 검증 실패 / 미등록 장치 → FailedReading 적재
      ├─ SensorData 저장 (PostgreSQL) + device.markSeen()
      ├─ AnomalyDetector 판정 → 초과 시 Alert 생성
      └─ 커밋 후(AFTER_COMMIT) SseBroadcastEvent 발행 → 대시보드 push
```

수신 경로 밖 스케줄러:
- FreshnessScheduler: 기대 주기 초과한 침묵 장치 감지
- AlertEnrichmentScheduler: ax.enabled 시 AX 호출로 Alert evidence/recommendation 보강 (HTTP는 트랜잭션 밖)

### 역할과 접근 제어
4단계 역할: `SYSTEM_ADMIN`(전체), `FACTORY_ADMIN`(소속 공장), `MEMBER`(소속 구역 읽기/쓰기), `VIEWER`(소속 구역 읽기 전용).
`AccessControlService`가 공장/구역/구역소속 계층으로 접근 범위를 계산하며, 권한 부족은 `AccessDeniedException`(403)으로 처리.

### 인증 흐름
모든 요청은 `JwtFilter`를 통과. 공개 엔드포인트:
- `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`
- `POST /sensor-data` (게이트웨이, 장치 to 서버)
- `/swagger-ui/**`, `/api-docs/**`, 정적 파일

나머지는 JWT Bearer 토큰 필수.

### 프로파일
- 기본 활성 프로파일은 `local` (application.yml, SQL 로그 등 개발 편의 설정)
- `JWT_SECRET`은 환경변수로 주입 (기본값 없음)
- GCP/Cloud Run 배포 계획은 접음. prod 프로파일과 `application-prod.yml`은 없음 (지난 배포 회고는 `docs/gcp-archive.md`)

### CI
GitHub Actions (`.github/workflows/ci.yml`): JDK 17 + Gradle `build` (H2 테스트 포함).
