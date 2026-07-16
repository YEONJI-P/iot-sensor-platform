# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 구조 (모노레포)

중립 루트 + 서비스별 도메인 폴더. 루트는 어떤 서비스도 소유하지 않는다.

```
services/
├── backend/     Spring Boot API (build.gradle·gradlew·src, 이 안에서 gradle 실행)
├── explain/     FastAPI 이상 설명·진단 서비스 (uv, 자체 Dockerfile)
└── simulator/   Python 시뮬레이터 + seed.sql + data(리플레이용 실측 CSV)
docs/ · docker-compose.yml · .github/ · README.md  ← 루트(프로젝트 공통)
```

gradle 명령은 반드시 `services/backend/`에서 실행한다.

## Commands

### 로컬 개발 환경 시작
```bash
# 공용 PostgreSQL(외부, DB·계정 sensor_monitor)이 실행 중이어야 함
# 앱 기본값: jdbc:postgresql://localhost:5432/sensor_monitor (다르면 DB_* env 로 재정의)

# JWT 서명 키 주입 (기본값 없음, 미설정 시 부팅 실패)
export JWT_SECRET=$(head -c 48 /dev/urandom | base64)

# 애플리케이션 실행 (services/backend/)
cd services/backend && ./gradlew bootRun
```

컨테이너 데모: `docker-compose up --build` 가 자체 postgres + backend + explain 을 함께 띄운다(공용 DB 불필요, postgres 는 호스트 5433 노출). compose 는 각 서비스의 `.env`(`services/backend/.env`, `services/explain/.env`)를 `env_file` 로 읽으므로 미리 각 폴더에서 `cp .env.example .env` 해둔다(없어도 앱 기본값으로 뜨지만 backend 는 `JWT_SECRET` 이 필수). backend 는 멀티스테이지 Dockerfile 로 컨테이너 안에서 빌드된다. simulator 는 상시 서비스가 아니라 `seed` 프로파일(`docker-compose --profile seed run --rm simulator --all`). 일상 개발은 위 bootRun(공용 DB) 경로를 쓰고, compose 전체 up 대신 필요한 서비스만(`up explain`) 선택 기동한다.

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
- Refresh Token은 PostgreSQL `refresh_tokens` 테이블에 저장 (회전·서버측 로그아웃)
- JWT (jjwt), stateless 인증
- SSE (SseEmitter), 대시보드 실시간 스트림
- Swagger/OpenAPI, `/swagger-ui/index.html` 에서 API 문서 확인
- explain 분석 서비스 (services/explain): Python 3.11, FastAPI, uv. Spring이 HTTP로 호출하는 별도 프로세스

메시지 버스(Kafka)는 제거됨. 소비자가 하나뿐이라 과설계였고, 수신을 동기 처리로 단순화 (설계 근거는 README 설계 메모 참고).

### 도메인 구조
`src/main/java/dev/bugi/sensor/` 하위 도메인:

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
| `explain/` | explain 서비스 HTTP 클라이언트(ExplainClient/HttpExplainClient), DTO, 설정(explain.base-url/enabled) |
| `global/` | 공통 예외 처리, 접근 제어(AccessControlService), 공유 설정 |

Python 분석 서비스는 `services/explain/` (FastAPI). 엔드포인트 `POST /explain/anomaly`, `POST /explain/freshness`. provider 추상화(기본 echo, gemini 선택). 탐지는 Spring 규칙, 설명과 진단만 LLM.

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
- FreshnessScheduler: 기대 주기 초과 침묵 감지. 같은 구역 장치가 동시 침묵하면 사이트 사건(계획정지·게이트웨이 장애)으로 보고 WARNING 집계 1건, 이웃이 정상 수신 중인데 단독 침묵하면 개별 고장으로 CRITICAL + explain/freshness 원인진단(explain.enabled 시). 코호트/개별 각각 디바운스
- AlertEnrichmentScheduler: explain.enabled 시 explain 호출로 Alert evidence/recommendation 보강 (HTTP는 트랜잭션 밖)

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

## 환경변수(env) 구조

값의 성격으로 위치를 나눈다. **키 하나는 정본 파일 하나에만** 둔다(중복 금지).

- **서비스별 설정** → 각 `services/<svc>/.env` 가 정본. backend = `JWT_SECRET`, `EXPLAIN_ENABLED`; explain = `EXPLAIN_PROVIDER`, `GEMINI_API_KEY`, `MODEL_NAME`, `REQUEST_TIMEOUT` 등. 예시는 같은 폴더의 `.env.example`.
- **오케스트레이션 토폴로지** → `docker-compose.yml` 의 `environment:`. 내부 호스트명(`postgres`, `explain`), 데모 DB 크레덴셜 등 compose 에서만 유효한 값. `env_file` 보다 우선 적용된다.
- **루트 `.env` 는 쓰지 않는다.** (compose 는 각 서비스 `.env` 를 `env_file: required:false` 로 읽는다 — 없어도 앱 기본값으로 뜬다)

새 env 키는 그 값을 소비하는 **서비스의 `.env.example` 에만** 추가한다. 루트나 다른 서비스에 중복 정의하지 말 것.

## 포트

값은 env로 override 가능하므로 어디에 clone하든 기본값으로 단독 실행된다.

- backend API: `SERVER_PORT`(= `server.port`, 기본 `23100`)
- frontend: `23000` (있을 경우)
- DB: `DB_*` (기본 로컬 PostgreSQL `5432/sensor_monitor`)
- 데모 compose(`docker-compose up`)는 자체 postgres를 호스트 `5433`으로 노출(공용 DB와 별개, 포폴용 독립 스택). backend는 호스트 `8080` 유지(`SERVER_PORT=8080`으로 핀 고정)

포트를 코드/설정에 하드코딩하지 말고 `server.port`로 주입한다. 같은 머신에서 여러 서비스/워크트리를 동시에 띄울 때만 충돌 가능성이 있고, 그땐 오프셋(`23100 + INSTANCE`, 예: 워크트리1=`23101`)으로 격리한다.

> 여러 로컬 서비스 간 포트 배정을 한눈에 보는 전체 대장은 작성자의 로컬 개발 인프라 쪽에서 따로 관리한다(이 repo 단독 실행엔 불필요, 로컬에서 새 포트를 배정할 때만 참조).

## Health 엔드포인트

규약은 `~/projects/CLAUDE.md` §6(응답 `{"status":"UP"}`·무인증·상세 미노출·경로는 프레임워크 관례). 이 repo 구현:

- backend: `GET /actuator/health` (기본 `23100`, 데모 compose는 `8080`). `exposure.include: health` + `show-details: never`, SecurityConfig에서 이 경로만 permitAll — 다른 actuator 엔드포인트는 401.
- explain: `GET /health` (`23200`).

## 시각(timestamp) 처리 규칙

"UTC로 저장하고, 표시할 때만 변환한다"를 따른다.

- DB 컬럼은 `timestamptz`. `timestamp`(without time zone) 쓰지 말 것.
- 엔티티/DTO의 시각 필드는 `Instant`(필요 시 `OffsetDateTime`). `LocalDateTime` 금지 — 타임존 정보가 없어 서버 로컬 타임존에 의존한다.
- 현재 시각은 `LocalDateTime.now()`/`Instant.now()` 직접 호출 대신 주입한 `Clock`으로(`clock.instant()`) 얻는다. 테스트에서 고정 가능.
- API 응답은 ISO 8601 UTC 문자열(끝에 `Z`). Jackson이 epoch 숫자로 내보내지 않게 `spring.jackson.serialization.write-dates-as-timestamps=false`.
- DB의 `timezone`(GUC)과 컨테이너 `TZ`는 건드리지 말 것 — UTC 기본값 유지.
- 사용자 타임존 변환은 프론트가 담당한다. 백엔드는 UTC로만 내보낸다.
- 예외: 이메일/PDF/리포트처럼 서버가 최종 렌더하거나 "그 나라 자정" 기준 집계가 필요할 때만 백엔드에서 명시적으로 zone 변환한다.

> 진행 상황: `LocalDateTime`→`Instant` 전환 완료(Phase 1~3, main 병합·미푸시). 엔티티/DTO 시각은 모두 `Instant`(timestamptz), now()는 주입 `Clock`, 감사 필드(`@CreatedDate`/`@LastModifiedDate`)는 `DateTimeProvider`로 Clock에 태움. dev DB(5432) 컬럼도 timestamptz로 마이그레이션 완료.

## 설계: 텔레메트리(DeviceStatus)와 설정(Device) 분리

`Device`는 **설정**(이름·타입·위치·임계값·기대주기)만 갖는다. **런타임 상태는 전부 `DeviceStatus`**(테이블 `device_status`, `device_id` 공유 PK)로 간다 — 수신 하트비트 `lastSeenAt`, 알람 상태 `inAlarm`/`lastAlertAt`. 되돌리지 말 것.

- 이유: 이들은 수신·알람 전이마다 갱신되는 런타임 값이라 Device에 두면 (1) Device 행이 dirty돼 `@LastModifiedDate`인 `Device.updatedAt`을 오염시키고(설정 변경 감사가 무의미해짐), (2) 설정 행이 계속 다시 쓰여 뜨거워진다.
- **새 런타임/텔레메트리 필드는 Device가 아니라 DeviceStatus에 추가할 것.**
- 수신 경로: `SensorDataService.markSeen()`이 `DeviceStatus`를 upsert하고 그 인스턴스를 반환 → `evaluateAlarm(device, status, value)`가 알람 상태를 여기서 읽고 쓴다. Device는 건드리지 않는다.
- freshness 조회: `DeviceStatusRepository.findMonitoredWithDeviceAndZone()`(device·zone JOIN FETCH). JOIN이라 수신 이력 없는 장치는 자연히 제외된다.
- `DeviceStatus`엔 감사 필드를 두지 않는다(텔레메트리라 불필요).
