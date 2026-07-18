# Sensor Monitor

> 제조 설비 센서 데이터를 수집하고 이상 발생 시 근거와 함께 알림을 생성하는 센서 시계열 수집, 모니터링 백엔드

<br>

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.x-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Python](https://img.shields.io/badge/Python_3.11-3776AB?style=flat-square&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=flat-square&logo=fastapi&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)

<br>

GitHub: https://github.com/YEONJI-P/sensor-monitor

<br>

---

## 목차

1. [프로젝트 소개](#1-프로젝트-소개)
2. [범위와 경계](#2-범위와-경계)
3. [기술 스택](#3-기술-스택)
4. [시스템 아키텍처](#4-시스템-아키텍처)
5. [ERD](#5-erd)
6. [API 명세](#6-api-명세)
7. [주요 기능](#7-주요-기능)
8. [확장 로드맵](#8-확장-로드맵)
9. [실행 방법](#9-실행-방법)
10. [설계 메모](#10-설계-메모)

---

## 1. 프로젝트 소개

제조 설비, 공장 환경에서 발생하는 센서 데이터를 수집하고, 임계값을 벗어난 이상 징후가 보이면 근거와 함께 알림을 생성하는 모니터링 백엔드입니다. 수집한 센서 시계열과 알림 이력은 영속 저장되어 사후 조회할 수 있습니다.

사번(employeeId) 기반의 승인제 회원 관리와 4단계 역할 기반 접근 제어(RBAC)를 통해, 공장, 구역 단위로 접근 범위를 제한합니다.

---

## 2. 범위와 경계

이 프로젝트는 게이트웨이가 HTTP/JSON으로 전달한 센서 데이터를 받아 저장하고 감시하는 백엔드입니다. 현장 프로토콜(Modbus, OPC-UA)의 수집과 변환은 범위 밖입니다.

```mermaid
graph LR
    PLC[현장 설비<br>PLC / 센서]
    GW[엣지 게이트웨이<br>프로토콜 변환]
    API[이 프로젝트<br>수집 + 모니터링 백엔드]

    PLC -->|Modbus / OPC-UA| GW
    GW -->|HTTP / JSON| API
```

실제 실시간 센서 대신, 저장된 센서 시계열을 시간 순으로 흘려보내 수신을 재현합니다.

---

## 3. 기술 스택

| 영역 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x, Spring Security |
| Auth | JWT (JSON Web Token), Refresh Token 회전 |
| ORM | Spring Data JPA (Hibernate) |
| Database | PostgreSQL, Flyway |
| Realtime | Server-Sent Events (SSE) |
| AI Service | Python 3.11, FastAPI, uv |
| API Docs | Swagger (springdoc-openapi) |
| Test | JUnit5, Mockito, H2(부팅 스모크), Testcontainers(DB 계층), pytest |
| Container | Docker, Docker Compose |
| CI | GitHub Actions |

---

## 4. 시스템 아키텍처

센서 데이터 수신은 별도 메시지 버스 없이 동기 처리합니다. 수신 요청이 들어오면 한 트랜잭션 안에서 센서 데이터를 저장하고, 장치의 마지막 수신 시각을 갱신하고, 임계값 초과를 판정해 알림을 생성합니다. 저장과 알림 이벤트는 트랜잭션 커밋 후 SSE로 대시보드에 실시간 전달됩니다.

주기 스케줄러 두 개가 수신 경로 밖에서 동작합니다. 하나는 기대 수신 주기를 넘긴 침묵 장치를 감지하고, 다른 하나는 생성된 알림의 근거와 권고를 채우기 위해 별도 Python 분석 서비스(explain)를 HTTP로 호출합니다. 이상 탐지는 규칙 기반이고, 설명과 진단만 LLM이 담당합니다.

```mermaid
graph TD
    SIM[센서 시뮬레이터 / 게이트웨이]
    CLI[클라이언트<br>Swagger, 대시보드]

    subgraph API[Spring Boot API Server]
        AUTH[Auth<br>JWT, 승인제 가입]
        ADMIN[Admin<br>사용자 승인, 공장/구역 관리]
        DEVICE[Device<br>장치 CRUD]
        SENSOR[Sensor Data<br>수신, 임계값 판정, 알림 생성]
        SSE[SSE<br>대시보드 실시간 스트림]
        SCHED[스케줄러<br>freshness 감지, 알림 근거 보강]
    end

    EXPLAIN[explain 분석 서비스<br>Python, FastAPI]
    DB[(PostgreSQL)]

    SIM -->|POST /sensor-data| SENSOR
    CLI --> AUTH
    CLI --> ADMIN
    CLI --> DEVICE
    CLI -->|GET /dashboard/stream| SSE
    SENSOR -->|저장 + 임계값 초과 시 알림| DB
    SENSOR -->|커밋 후 이벤트| SSE
    SCHED -->|HTTP 요청-응답| EXPLAIN
    SCHED --> DB
    AUTH -->|Refresh Token 저장| DB
```

---

## 5. ERD

```mermaid
erDiagram
    factories {
        bigint id PK
        varchar name
        varchar description
        timestamptz created_at
    }

    zones {
        bigint id PK
        bigint factory_id FK
        varchar name
        varchar description
        timestamptz created_at
    }

    zone_users {
        bigint id PK
        bigint zone_id FK
        bigint user_id FK
        timestamptz created_at
    }

    users {
        bigint id PK
        varchar employee_id UK "NOT NULL"
        varchar name "NOT NULL"
        varchar email "NULLABLE, UNIQUE"
        varchar password
        bigint factory_id FK "NULLABLE"
        varchar role "SYSTEM_ADMIN/FACTORY_ADMIN/MEMBER/VIEWER"
        varchar status "PENDING/ACTIVE/REJECTED"
        timestamptz created_at
        timestamptz updated_at
    }

    device {
        bigint id PK
        bigint zone_id FK
        varchar code UK "물리 노드 식별자(예: CMAPSS-U1)"
        varchar name
        varchar location
        int expected_interval_seconds "NULLABLE"
        timestamptz created_at
        timestamptz updated_at
    }

    device_status {
        bigint device_id PK "device와 공유 PK(@MapsId)"
        timestamptz last_seen_at "수신 하트비트, NULLABLE"
    }

    sensor_channel {
        bigint id PK
        bigint device_id FK
        varchar code "device 안에서 유일(UK: device_id+code)"
        varchar unit "NULLABLE, enum 아님"
        varchar quantity_kind "NULLABLE, 자유 문자열(enum 아님)"
        double threshold_value "NULLABLE"
        varchar threshold_direction "ABOVE/BELOW, NULLABLE"
        timestamptz created_at
        timestamptz updated_at
    }

    channel_status {
        bigint channel_id PK "sensor_channel과 공유 PK(@MapsId)"
        boolean in_alarm "알람 상태(엣지 트리거)"
        timestamptz last_alert_at "NULLABLE"
    }

    measurement_batch {
        bigint id PK
        bigint device_id FK
        timestamptz observed_at "원본 관측 시각, NULLABLE(없으면 received_at)"
        timestamptz received_at "서버 Clock 주입"
        bigint source_seq "NULLABLE, cycle 번호·행 인덱스"
    }

    sensor_reading {
        bigint id PK
        bigint batch_id FK
        bigint channel_id FK
        double value "UNIQUE(batch_id, channel_id)"
    }

    alerts {
        bigint id PK
        bigint device_id FK "NULLABLE - freshness alert는 device만"
        bigint channel_id FK "NULLABLE - 임계 alert만 세팅"
        bigint batch_id FK "NULLABLE - 임계 alert만 세팅"
        double sensor_value
        double threshold_value
        varchar message
        varchar severity "INFO/WARNING/CRITICAL"
        varchar evidence "NULLABLE, LLM 보강"
        varchar recommendation "NULLABLE, LLM 보강"
        timestamptz created_at
        timestamptz updated_at
    }

    failed_readings {
        bigint id PK
        bigint device_id "NULLABLE, 구 scalar API 잔재(FK 아님)"
        varchar device_code "NULLABLE, 장치 없음 수신 실패"
        varchar channel_code "NULLABLE, 미지 채널 수신 실패"
        double value "NULLABLE"
        varchar reason "DEVICE_NOT_FOUND/UNKNOWN_CHANNEL/NULL_VALUE"
        timestamptz created_at
    }

    factories ||--o{ zones : "구역 보유"
    factories ||--o{ users : "소속"
    zones ||--o{ zone_users : "소속 사용자"
    users ||--o{ zone_users : "소속 구역"
    zones ||--o{ device : "설치"
    device ||--|| device_status : "런타임 상태(1:1)"
    device ||--o{ sensor_channel : "측정 채널"
    sensor_channel ||--|| channel_status : "알람 상태(1:1)"
    device ||--o{ measurement_batch : "관측 batch"
    measurement_batch ||--o{ sensor_reading : "batch 내 판독"
    sensor_channel ||--o{ sensor_reading : "채널별 판독"
    sensor_channel ||--o{ alerts : "임계 초과"
    measurement_batch ||--o{ alerts : "발생 근거"
    device ||--o{ alerts : "freshness"
```

---

## 6. API 명세

Swagger UI: `http://localhost:23100/swagger-ui/index.html` (컨테이너 데모는 `8080`)

![Swagger UI](docs/images/swagger.png)

### Auth

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| POST | `/auth/register` | 가입 신청 (status=PENDING) | 불필요 |
| POST | `/auth/login` | 로그인, ACTIVE 상태만 허용 | 불필요 |
| POST | `/auth/refresh` | Access Token 재발급 | 불필요 |

### Admin (FACTORY_ADMIN 이상)

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| GET | `/admin/users` | 사용자 목록 (FACTORY_ADMIN은 소속 공장만) | JWT |
| GET | `/admin/users/pending` | 승인 대기 목록 (FACTORY_ADMIN은 소속 공장만) | JWT |
| PATCH | `/admin/users/{id}/approve` | 가입 승인 — ACTIVE 전환 + 역할 부여 + 구역 배정 (body: `role`, `zoneIds`) | JWT |
| PATCH | `/admin/users/{id}/reject` | 가입 반려, REJECTED 전환 | JWT |
| GET, POST | `/admin/factories` | 공장 조회, 등록 (SYSTEM_ADMIN) | JWT |
| PUT, DELETE | `/admin/factories/{id}` | 공장 수정, 삭제 (SYSTEM_ADMIN) | JWT |
| GET, POST | `/admin/zones` | 구역 조회, 등록 | JWT |
| PUT, DELETE | `/admin/zones/{id}` | 구역 수정, 삭제 | JWT |
| POST | `/admin/zones/{id}/users` | 구역에 사용자 추가 | JWT |
| DELETE | `/admin/zones/{id}/users/{userId}` | 구역에서 사용자 제거 | JWT |

### Device (조회는 인증, 쓰기는 SYSTEM_ADMIN, FACTORY_ADMIN, MEMBER)

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| GET | `/devices` | 내 장치 목록 | JWT |
| POST | `/devices` | 장치 등록 | JWT |
| PUT | `/devices/{id}` | 장치 수정 | JWT |
| DELETE | `/devices/{id}` | 장치 삭제 | JWT |

### Sensor Data

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| POST | `/sensor-data` | 배치 수신 (게이트웨이·시뮬레이터 → 서버). body: `deviceCode`, `observedAt?`, `sourceSeq?`, `measurements`(채널 code→value map) | `X-Ingest-Key` |

> 한 물리 노드(`deviceCode`)의 한 관측 시점(batch) 값 묶음을 한 요청으로 받습니다. 미지 채널·null 값은 예외 없이 부분 실패로 처리해 응답 `rejected`에 채널 코드와 사유(`UNKNOWN_CHANNEL`/`NULL_VALUE`)를 담고, 나머지 채널은 정상 저장합니다. 응답은 `batchId`·`deviceId`·`deviceCode`·`observedAt`·`receivedAt`·`savedCount`·`rejected`를 반환하며, 장치 없음은 404(batch 미생성), 요청 채널 전부가 미지·무효면 422(batch 미생성)입니다. 이전의 scalar 조회 API(`GET /sensor-data`, `GET /sensor-data/{deviceId}`)는 이 모델 전환과 함께 제거됐고, 조회는 아래 Channel API로 옮겼습니다.

> 수신 경로는 사람용 JWT와 분리한 공유 키를 사용합니다. backend와 simulator에 같은 `INGEST_API_KEY`를 설정하면 simulator가 `X-Ingest-Key` 헤더로 전송합니다. 키가 없으면 두 서비스 모두 시작 단계에서 실패하고, 헤더가 없거나 다르면 `401`과 `UNAUTHORIZED` 응답을 반환합니다.

### Channel

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| GET | `/channels` | 내 접근 범위의 채널 목록 (대시보드 드롭다운 소스) | JWT |
| GET | `/channels/{id}/readings?limit=` | 채널별 최근 판독 (`observed_at desc`, 기본·상한 500건, 서버 순간 판정 `anomaly`) | JWT |
| POST | `/devices/{deviceId}/channels` | 채널 등록 | JWT |
| PUT | `/channels/{id}` | 채널 수정 (임계값·임계 방향 등) | JWT |

### Alert

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| GET | `/alerts` | 전체 알림 조회 (페이지네이션, `?page=&size=&sort=&deviceId=`; `deviceId` 선택) | JWT |
| GET | `/alerts/channel/{channelId}` | 채널별 알림 조회 | JWT |
| GET | `/alerts/recent?channelId=&limit=` | 채널별 최근 알림 (대시보드) | JWT |
| GET | `/alerts/daily-count?channelId=&days=` | 채널별 일자별 알림 수 (대시보드) | JWT |

> 임계 alert는 `deviceId`·`channelId` 둘 다 채워지고, freshness(수신 끊김) alert는 채널이 없어 `deviceId`만 채워집니다(`channelId=null`).

### 실시간 스트림 (SSE)

| Method | Endpoint | 설명 | 인증 |
|---|---|---|---|
| GET | `/dashboard/stream?token=` | 접근 범위 내 센서, 알림 이벤트 실시간 스트림 | 쿼리 토큰 |
| GET | `/dashboard/overview` | 접근 가능한 공장·구역·장치와 채널 최신 상태를 한 번에 조회 | JWT |

> EventSource가 헤더를 못 실어 Access Token을 쿼리로 받습니다. 구독자는 자신의 접근 가능 장치로 이벤트가 필터링됩니다. `sensor-data` 이벤트는 batch 단위로 채널 판독 배열(`readings`)을 한 번에 담아 보내며, 각 판독의 `anomaly`는 서버 `ThresholdDetector`가 계산한 순간 임계 판정입니다(알람의 쿨다운·해제 상태와는 별개). 연결이 닫히면 refresh token으로 access token을 갱신해 재구독하고, 30초마다 overview를 다시 조회해 유실 이벤트를 보정합니다.

> overview의 freshness는 `NOT_MONITORED`(기대 주기 미설정), `NEVER_SEEN`(수신 이력 없음), `ONLINE`, `STALE` 네 단계입니다. 정시 보고 장치가 네트워크 지연으로 깜빡이지 않도록 마지막 수신 후 기대 주기의 2배까지 `ONLINE`, 그보다 오래 침묵하면 `STALE`로 표시합니다.

### explain 분석 서비스 (Python, 로컬 `http://localhost:23200` · 컨테이너 데모 `8000`)

Spring이 스케줄러에서 HTTP로 호출하는 별도 서비스입니다. 탐지는 Spring의 규칙이 담당하고, 이 서비스는 설명과 진단만 생성합니다.

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/explain/anomaly` | 알림 근거(evidence)와 권고(recommendation) 생성 |
| POST | `/explain/freshness` | 장치 침묵 원인 진단 |

> LLM provider는 인터페이스로 분리돼 있습니다. 기본값은 키가 필요 없는 `echo`이고, 환경변수로 `gemini`로 교체할 수 있습니다.

---

## 7. 주요 기능

실시간 대시보드 — SSE로 센서값이 실시간 갱신되고, 임계값 초과 알림에는 LLM(explain 서비스)이 생성한 근거·권고가 붙습니다.

![센서 대시보드](docs/images/dashboard.png)

![장치 상세와 채널 현황](docs/images/dashboard-device-detail.png)

### 승인제 사용자 관리와 접근 제어

- 사번(employeeId) 기반 가입 신청, 가입 즉시 `PENDING` 상태로 저장
- `FACTORY_ADMIN` 이상의 관리자가 승인 또는 반려(`REJECTED`) 처리. 승인 시 `ACTIVE` 전환과 함께 역할 부여, 소속 구역 배정을 한 트랜잭션에서 수행
- `FACTORY_ADMIN`은 자신의 소속 공장 사용자만 조회, 승인할 수 있고 (`SYSTEM_ADMIN`은 전체), 부여 가능한 역할도 자기 역할 이하로 제한
- `PENDING`, `REJECTED` 상태에서 로그인 시 `DisabledException`으로 차단
- 4단계 역할 기반 접근 제어

  | 역할 | 범위 |
  |---|---|
  | `SYSTEM_ADMIN` | 전체 공장, 장치 |
  | `FACTORY_ADMIN` | 소속 공장의 구역, 장치, 사용자 관리 |
  | `MEMBER` | 소속 구역 읽기, 쓰기 (장치 관리) |
  | `VIEWER` | 소속 구역 읽기 전용 (장치 변경 불가) |

- 공장(Factory), 구역(Zone) 계층과 구역 소속 관계로 접근 범위를 계산하는 `AccessControlService`
- prod의 공개 데모 토폴로지는 Flyway V3가 자동 투입하고, local의 계정 포함 데모 데이터는 `services/simulator/seed.sql`로 별도 투입

### 센서 데이터 수신과 알림

- `POST /sensor-data` 수신 시 한 트랜잭션에서 센서 데이터 저장, 장치 수신 시각 갱신, 임계값 초과 판정, 알림 생성
- 알림은 **엣지 트리거**로 생성 — 정상에서 임계값 초과로 넘어가는 순간 한 건만 만들고, 초과가 지속되는 동안은 억제합니다. 값이 임계값 아래(히스테리시스 밴드)로 복귀하면 알람을 해제해 다음 초과를 다시 감지합니다. 초과가 지속되는 구간에서 같은 사건이 수십 건으로 도배되는 것을 막습니다
- severity는 초과 폭으로 판정(임계값 대비 여유가 크면 `CRITICAL`). 장치 런타임 상태(알람 여부·마지막 수신 시각)는 설정과 분리해 `device_status` 테이블에서 관리
- 별도 메시지 버스 없이 동기 처리 (설계 근거는 아래 설계 메모 참고)
- 이상 판정은 `AnomalyDetector` 전략 인터페이스로 분리(현재 `ThresholdDetector`), 판정 로직 교체 가능
- 검증 실패, 미등록 장치 요청은 조용히 버리지 않고 `failed_readings`에 사유와 함께 적재
- 알림은 severity(INFO/WARNING/CRITICAL)와 근거(evidence), 권고(recommendation) 필드를 가지며, 근거와 권고는 explain 서비스가 사후 보강

### 장치 freshness 감지

- 장치 설정에 기대 수신 주기(`expectedIntervalSeconds`)를 두고, 마지막 수신 시각(`lastSeenAt`)은 런타임 상태라 `device_status`에 두어 수신마다 갱신
- 대시보드 상태는 기대 주기의 2배까지 `ONLINE` 유예를 두고 이후 `STALE`로 표시해 정시 수신의 작은 지연이 화면을 깜빡이지 않게 함
- 주기 스케줄러가 기대 주기를 넘겨 침묵한 장치를 감지 (데이터가 안 오는 상황을 신호로 포착)
- 같은 구역 장치가 동시에 침묵하면 사이트 사건(계획 정지, 게이트웨이 장애)으로 보고 구역 한 건으로 집계(`WARNING`), 이웃이 정상 수신 중인데 단독 침묵하면 개별 고장으로 `CRITICAL` + explain 원인진단

### LLM 기반 이상 설명 (explain 서비스)

- 별도 Python/FastAPI 서비스가 알림 근거, 권고와 침묵 원인 진단을 생성
- 탐지는 규칙, 설명과 진단만 LLM이 담당
- provider를 인터페이스로 분리해 LLM 교체 가능(기본 `echo`, `gemini` 선택)

### 인증

- JWT 기반 stateless 인증, Refresh Token 은 PostgreSQL 에 저장
- Refresh Token 회전, 불일치 시 저장 토큰을 삭제해 강제 로그아웃 처리
- Access, Refresh 토큰에 `type` 클레임을 두어 Refresh 토큰으로는 API에 접근 불가

### 센서 시뮬레이터 (`services/simulator/simulator.py`)

- 실제 센서처럼 서버 외부에서 `POST /sensor-data`를 직접 호출
- `replay`: 공개 실측 시계열(C-MAPSS 엔진, CNC 밀링)을 시간 순으로 재생 — 원본 한 행이 물리 device 하나의 관측 batch 하나(`measurements` 채널 code→value map)로 요청 1건이 됩니다
- `synthetic`: 정상 랜덤워크에 간헐적 임계 초과·회복 구간을 섞어 무제한 생성. 운영 요일·시간대 밖에는 프로세스를 종료하지 않고 전송만 대기합니다
- 대상 device(`deviceCode`), 전송 간격, 전송 수, 난수 seed와 운영시간을 CLI 인자로 지정

### 실시간 대시보드

- 공장·구역별 장치 카드 overview에서 freshness, 마지막 수신, 현재 알람 수를 한눈에 표시
- 장치 상세에서 전체 채널 최신값을 함께 보고 선택 채널의 라인 차트와 최근 알림을 확인
- SSE(`/dashboard/stream`) batch 한 건으로 같은 장치의 여러 채널을 함께 갱신하고, 30초 polling으로 상태를 재동기화

---

## 8. 확장 로드맵

### 완료

- JWT 인증, 인가, 사번 기반 로그인, 승인제 가입
- 4단계 역할 기반 접근 제어, 공장, 구역 계층 접근 제어
- 가입 승인 워크플로 (역할 부여 + 구역 배정, FACTORY_ADMIN 소속 공장 스코핑)
- 동기 센서 수신 파이프라인 (수신, 저장, 임계값 판정, 알림)
- 이상 판정 로직 전략화 (`AnomalyDetector` 인터페이스로 분리)
- 알림 스키마 확장 (severity, 근거, 권고 필드)와 실패 수신 적재
- 장치 freshness 감지 (구역 코호트 판정으로 오탐 억제, 침묵 원인 explain 진단)
- SSE 기반 실시간 대시보드 (접근 범위 스코핑)
- LLM 기반 이상 근거, 원인 진단 (Python 분석 서비스 HTTP 연동)
- 실측 공개 센서 시계열(C-MAPSS 엔진, CNC 밀링) 리플레이로 시뮬레이터 데이터 교체
- 운영시간을 반영한 무제한 합성 데이터 스트림과 Compose live 프로파일
- Refresh Token 저장·회전 (PostgreSQL)

### 향후

- explain provider Gemini 실호출 (현재 기본 `echo`, 키 주입 시 전환)
- MQTT 수신 경로 도입 (엣지 게이트웨이와의 표준 연동)
- 대용량 시계열 저장소(TimescaleDB) 검토

---

## 9. 실행 방법

### 사전 요구사항

- Java 17
- 컨테이너 실행 시 Docker와 Docker Compose

Compose는 두 경로만 유지합니다. 직접 개발 실행은 별도 Compose가 아닙니다.

| 경로 | 파일 | PostgreSQL | 용도 |
|---|---|---|---|
| 독립 풀 데모 | `docker-compose.yml` | 전용 컨테이너·volume 포함 | 로컬 통합 실행, 평가자 데모 |
| 홈서버 추가 설치 | `docker-compose.home.yml` | 기존 인스턴스의 별도 `sensor_monitor` database | 운영 blocker 해결 후 독립 앱 stack으로 설치 |

두 Compose 파일은 각각 단독 실행합니다. 홈서버 파일을 독립 데모 파일 위에 override로 겹쳐 쓰지 않습니다.

### 로컬 실행 (공용 Postgres + bootRun)

일상 개발용. 공용 Postgres 를 쓰고 backend 만 로컬에서 띄운다.

```bash
git clone https://github.com/YEONJI-P/sensor-monitor.git
cd sensor-monitor

# 공용 PostgreSQL 준비 — DB·사용자 sensor_monitor 가 실행 중이어야 함
# (앱 기본값이 jdbc:postgresql://localhost:5432/sensor_monitor 를 가리킴)

# JWT 서명 키 설정, 기본값이 없어 미설정 시 부팅 실패 (셸 export 또는 IDE 실행 구성)
export JWT_SECRET=$(head -c 48 /dev/urandom | base64)

# 애플리케이션 실행 (Spring은 services/backend/)
cd services/backend
./gradlew bootRun

# (선택) explain은 services/explain/README.md에 따라 별도 실행
```

> 공용 Postgres 가 기본 접속정보와 다르면 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD` 를 셸 env 로 재정의합니다. Spring 은 `.env` 를 자동 로드하지 않으므로 위 값은 셸/IDE 에 직접 주입합니다.

### 독립 풀 데모 (`docker-compose.yml`)

평가자·로컬 통합 확인용입니다. postgres + backend + explain을 함께 기동하며 외부 DB가 필요 없습니다. backend는 local 프로파일로 스키마를 만든 뒤 `seed.sql`이 계정과 데모 토폴로지를 넣습니다.

```bash
# backend 설정 준비. JWT_SECRET은 실행 전에 반드시 교체
cp services/backend/.env.example services/backend/.env

# (선택) Gemini provider를 사용할 때만 준비
# cp services/explain/.env.example services/explain/.env

docker compose up --build -d  # postgres + backend + explain

# 초기 데이터(계정/장치/임계값) 적재
docker compose exec -T postgres psql -U sensor_monitor -d sensor_monitor < services/simulator/seed.sql

# (선택) 공개 실측 데이터 리플레이. command를 덧붙이지 않아 내부 backend 주소를 유지
bash services/simulator/data/download.sh
docker compose --profile replay run --rm simulator-replay

# (선택) 평일 08:00~18:00, 10초 간격 합성 데이터 상시 스트림
docker compose --profile live up -d simulator-live
```

> 컨테이너 postgres는 호스트 `5433`, backend는 `8080`, explain은 `8000`에 노출됩니다. backend는 내부 네트워크의 `postgres:5432`를 사용하므로 서비스 env의 `DB_*` 값보다 Compose 토폴로지 값이 우선합니다. `docker compose down`은 volume을 유지하고, `down -v`는 데모 DB를 삭제하므로 데이터 삭제 의도가 있을 때만 사용합니다.

### 홈서버 추가 설치 골격 (`docker-compose.home.yml`)

홈서버 기본 stack에는 sensor-monitor를 넣지 않습니다. 필요할 때 이 저장소의 독립 Compose를 추가하며, PostgreSQL 컨테이너나 DB volume은 만들지 않습니다. 다만 현재 파일은 아래 운영 blocker를 숨기지 않는 설치 골격이며, 조건을 해결하기 전에는 public 배포하지 않습니다.

선행 조건:

- 홈서버 기존 PostgreSQL 인스턴스에 별도 `sensor_monitor` database와 전용 role 생성
- PostgreSQL 컨테이너와 backend가 함께 참가할 외부 Docker network 준비
- reverse proxy와 backend가 함께 참가할 외부 Docker network 준비
- backend·explain·simulator 이미지를 같은 commit SHA로 GHCR에 수동 발행
- public reverse proxy에서 공유 키 수신 경로 `POST /sensor-data`를 추가 차단. live simulator는 내부 `app` network로 backend를 직접 호출
- 첫 로그인·승인을 위한 운영 관리자 bootstrap 절차 확정
- PostgreSQL host 디스크 사용량 경보와 임계 도달 시 `simulator-live` 중단 자동화

```bash
# Compose 보간값: 이미지 SHA와 외부 network 이름
cp .env.example .env

# backend 설정
cp services/backend/.env.example services/backend/.env

# services/backend/.env에서 JWT_SECRET과 홈서버 DB_URL/계정/비밀번호를 설정
# DB_URL의 host는 localhost가 아니라 외부 DB network의 PostgreSQL 서비스 이름

# (선택) Gemini provider를 사용할 때만 준비
# cp services/explain/.env.example services/explain/.env

# backend + explain. 빈 database에는 prod/Flyway V1~V3가 자동 적용
docker compose -f docker-compose.home.yml up -d

# synthetic 상시 스트림까지 활성화
docker compose -f docker-compose.home.yml --profile live up -d
```

`docker-compose.home.yml`의 simulator는 DB에 직접 연결하지 않고 backend HTTP API만 호출합니다. Compose를 내려도 외부 PostgreSQL database는 삭제되지 않습니다. V3는 공장·구역·device·채널만 만들며 로그인 사용자는 만들지 않습니다. `/sensor-data`는 `X-Ingest-Key` 공유 키로 보호되지만, 키 회전·전송량 제한·mTLS는 포함하지 않은 포트폴리오 수준의 경계입니다. public reverse proxy에서는 계속 수신 경로를 차단하고 simulator가 내부 network로 backend를 호출하게 둡니다.

### 테스트 실행

```bash
# backend
cd services/backend
./gradlew test

# simulator
cd ../simulator
python -m unittest -v test_simulator.py
```

> 테스트는 세 갈래입니다.
> - **컨텍스트 부팅 스모크**(`contextLoads`)는 인메모리 H2 로 동작해 별도 인프라 없이 실행됩니다(엔티티 매핑·설정 오류를 싸게 잡는 용도이며, DB 계층은 검증하지 않습니다). 설정은 `services/backend/src/test/resources/application.yml`.
> - **DB 계층 검증**(리포지토리·네이티브 쿼리·제약·컬럼 타입)은 Testcontainers 로 프로덕션과 동일한 `postgres:15` 를 띄워 검증하므로 **로컬에 도커가 실행 중이어야 합니다**. 컨테이너는 한 번만 떠서 모든 리포지토리 테스트가 재사용합니다.
> - **운영 스키마 검증**(`FlywayMigrationTest`)은 빈 `postgres:15`에 prod 프로파일을 적용해 Flyway V1/V2/V3 실행, 공개 데모 토폴로지와 Hibernate `ddl-auto=validate` 부팅을 함께 확인합니다.

### Swagger UI

```
http://localhost:23100/swagger-ui/index.html
```

> bootRun 기본 포트는 `23100`. 독립 풀 데모는 호스트 `8080`, 홈서버는 reverse proxy가 정한 공개 주소를 사용합니다.

### 로컬 데모 초기 데이터 투입 (`services/simulator/seed.sql`)

이 절은 Flyway를 끄고 Hibernate `ddl-auto=update`를 사용하는 local 실행 전용입니다. Spring Boot 기동 후 스키마가 준비된 상태에서 실행합니다. prod는 이 파일을 실행하지 않고 Flyway V3의 공장·구역·device·채널 토폴로지를 사용합니다.

> 기존 local DB가 이전 모델(방식 A, 채널=Device)로 이미 떠 있었다면 Hibernate `ddl-auto=update`는 컬럼·테이블을 삭제하지 않습니다. `device.type`·`device.threshold_value`·`sensor_data`·`device_status.in_alarm`/`last_alert_at`처럼 이번 전환에서 제거된 구 컬럼·테이블이 그대로 남아 새 엔티티·제약과 어긋날 수 있습니다. 이 모델 전환 이후의 로컬 개발은 기존 DB를 이어 쓰지 말고 빈 DB(스키마 재생성)에서 새로 시작하는 것을 권장합니다.

```bash
psql -U sensor_monitor -d sensor_monitor -f services/simulator/seed.sql
```

> 재실행이 필요한 경우 `seed.sql` 하단의 `TRUNCATE` 주석을 해제 후 먼저 실행하세요.
> 이 파일의 알려진 관리자·구성원 비밀번호는 로컬 시연용입니다. 공개 홈서버나 운영 DB에 투입하지 않습니다.

투입되는 샘플 계정

| employeeId | 이름 | Role | password |
|---|---|---|---|
| `SYSTEM` | 시스템 관리자 | SYSTEM_ADMIN | `admin1234!` |
| `ENG-ADMIN` | 엔진동 관리자 | FACTORY_ADMIN | `admin1234!` |
| `CNC-ADMIN` | 가공동 관리자 | FACTORY_ADMIN | `admin1234!` |
| `ENG-OP` | 엔진동 설비담당 | MEMBER | `op1234!` |
| `CNC-OP` | 가공동 설비담당 | MEMBER | `op1234!` |
| `ENG-VIEW` | 엔진동 열람 | VIEWER | `view1234!` |
| `CNC-VIEW` | 가공동 열람 | VIEWER | `view1234!` |

### 센서 시뮬레이터 실행 (`services/simulator/simulator.py`)

하나의 CLI가 `replay`와 `synthetic` 모드를 제공합니다. 둘 다 `POST /sensor-data`만 사용하며 DB에는 직접 접속하지 않습니다.

```bash
# replay: 데이터 내려받기(최초 1회) 후 원본 전체 재생
bash services/simulator/data/download.sh
pip install requests
python services/simulator/simulator.py --mode replay --all

# replay 일부만 재생
python services/simulator/simulator.py --mode replay \
  --devices CMAPSS-U1 CNC-EXP01 --interval 0.5 --limit 100

# synthetic: 평일 공장 운영시간에 10초 간격으로 무제한 생성
python services/simulator/simulator.py --mode synthetic --all \
  --interval 10 --active-days mon-fri --active-hours 08:00-18:00 \
  --timezone Asia/Seoul

# 테스트용 재현 가능 패턴 100건
python services/simulator/simulator.py --mode synthetic --all --limit 100 --seed 42
```

실행 전 `services/simulator/.env.example`을 참고해 `INGEST_API_KEY`를 환경변수로 주입합니다. 이 값은 backend의 `INGEST_API_KEY`와 같아야 하며 CLI 인자나 저장소 파일에 실제 키를 기록하지 않습니다.

`synthetic`은 `--limit 0`이 무제한이고 `Ctrl+C` 또는 컨테이너의 `SIGTERM`으로 정리 경로를 거쳐 종료합니다. 고정 `--seed`는 simulator 재시작을 요구하는 옵션이 아니라 같은 난수 순서를 재현하는 테스트 옵션이며, 생략하면 실행마다 다른 패턴을 만듭니다. 운영시간 밖에는 컨테이너를 종료하지 않고 전송만 대기합니다. 연결 실패 시 최대 60초까지 backoff하고 성공하면 원래 간격으로 돌아갑니다.

device는 `deviceCode`(`CMAPSS-U1`/`CMAPSS-U2`/`CNC-EXP01`)로 식별합니다. 이 code와 채널 code는 요청 대상 DB의 device/sensor_channel 데이터(독립 데모는 `seed.sql`, 홈서버 prod는 Flyway V3)와 일치해야 합니다. 현재 batch에는 replay/synthetic 출처 구분 필드가 없으므로 두 모드의 값을 같은 DB에 넣으면 데이터만 보고 출처를 구분할 수 없습니다.

### 환경변수

| 파일 | 필요한 실행 방식 | 사용자가 설정하는 값 |
|---|---|---|
| 루트 `.env` | 홈서버 | 이미지 SHA tag, 기존 DB network, reverse proxy network |
| `services/backend/.env` | 독립 풀 데모·홈서버 | JWT 서명 키, 센서 수신 `INGEST_API_KEY`. 홈서버는 DB URL·사용자·비밀번호도 설정 |
| `services/simulator/.env` | replay·live simulator | backend와 같은 `INGEST_API_KEY` |
| `services/explain/.env` | Gemini 사용 시에만 선택 | provider, API key, 필요한 경우 모델명 |

독립 풀 데모는 backend의 DB 값·프로파일·포트·explain 내부 주소를 Compose가 덮어쓰므로 JWT만 교체하면 됩니다. 홈서버 DB URL의 host는 `localhost`가 아니라 외부 DB network에서 해석되는 PostgreSQL 서비스 이름이어야 합니다. 기본 echo provider는 explain `.env` 없이 동작합니다.

`bootRun`은 `.env`를 자동으로 읽지 않으므로 직접 실행할 때는 셸이나 IDE에 환경변수를 주입합니다. simulator는 수신 키만 환경변수로 받고, 모드·간격·운영시간은 CLI 인자 또는 Compose의 `command`가 정합니다.

### 운영 DB 스키마와 Flyway

- backend의 `prod` 프로파일은 Flyway migration을 먼저 실행하고 Hibernate는 `ddl-auto=validate`로 결과만 검증합니다. 첫 스키마는 `services/backend/src/main/resources/db/migration/V1__initial_schema.sql`입니다.
- 독립 풀 데모 `docker-compose.yml`은 `SPRING_PROFILES_ACTIVE=local`을 명시하고 `ddl-auto=update` + `seed.sql` 경로를 유지합니다.
- 홈서버 `docker-compose.home.yml`은 `prod` 프로파일로 기존 PostgreSQL 인스턴스의 별도 database에 Flyway migration을 적용합니다.
- 빈 운영 DB와 접속 role은 배포 인프라가 먼저 만들어야 합니다. Flyway는 DB/role 생성이나 백업 도구가 아니며, 이미 만들어진 DB 안에서 schema와 명시적으로 버전 관리하는 기준 데이터만 적용합니다.
- V1은 schema만 만들고 checksum 고정을 위해 이후 수정하지 않습니다.
- V2(`V2__normalized_ingest_model.sql`)는 수신 모델을 "채널=Device"에서 물리 Device ─ SensorChannel ─ MeasurementBatch ─ SensorReading 정규화 모델로 전환하는 DDL입니다. `device.type`·`device.threshold_value` 제거와 `device.code`(UK) 추가, `sensor_channel`·`measurement_batch`·`sensor_reading`·`channel_status` 신설, `alert`에 `channel_id`·`batch_id` 추가, scalar 텔레메트리 테이블 `sensor_data` 제거를 포함합니다.
- V3(`V3__public_demo_topology.sql`)는 V2가 만든 새 스키마 위에, 공개 홈서버와 새 prod DB에 공통인 공장 2개·구역 3개·물리 device 3개·측정 채널 7개를 한 번만 넣습니다.
- V2·V3 모두 사용자, 구역 소속, 비밀번호를 만들지 않습니다. 따라서 공개 홈서버의 첫 계정과 최소 권한 bootstrap 절차는 배포 전에 별도로 확정해야 합니다.
- 독립 풀 데모는 Flyway를 실행하지 않으므로 V2·V3가 적용되지 않습니다. device/채널과 여러 역할 계정은 `services/simulator/seed.sql`을 수동 실행해 넣습니다.
- **seed.sql과 Flyway V3는 같은 토폴로지(공장 2·구역 3·device 3·채널 7)를 서로 다른 경로로 넣습니다.** 같은 DB에 둘 다 적용하지 않습니다 — 중복 적용하면 `device.code` UNIQUE 충돌과 `factories`/`zones` 중복이 납니다. 로컬은 seed.sql만, prod는 Flyway(V1+V2+V3)만 적용합니다.
- 운영 DB에 한 번 적용된 migration은 내용을 수정하지 않고 다음 변경을 새 `Vn__...sql` 파일로 추가합니다. V1·V2·V3 모두 적용 후 checksum 불변 대상입니다.

#### Hibernate가 이미 만든 DB의 1회 전환

Flyway history가 없는데 테이블이 들어 있는 DB는 prod 첫 기동이 의도적으로 실패합니다. 자동으로 기존 스키마를 정상이라고 간주하면 누락 컬럼이나 제약을 숨길 수 있기 때문입니다.

1. DB를 백업하고 복구 가능 여부를 확인합니다.
2. 실제 테이블·컬럼·제약·인덱스가 V1+V2가 만드는 스키마와 현재 엔티티에 맞는지 비교합니다. V3와 같은 이름의 데모 공장·구역·device(code)가 이미 있다면 중복 삽입과 `device.code` 충돌을 피할 별도 전환 migration을 먼저 설계합니다. history를 임의 수정하지 않습니다.
3. V1과 같음이 확인된 기존 DB에만 아래 두 환경변수를 **한 번의 prod 기동에만** 추가합니다.

   ```text
   SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
   SPRING_FLYWAY_BASELINE_VERSION=1
   ```

   이 기동은 V1 SQL을 실행하지 않고 기존 스키마를 version 1로 기록한 뒤 V2·V3를 적용하고 Hibernate validation을 수행합니다. migration이나 validation이 실패하면 배포를 중단하고 스키마·기존 데이터 차이를 수정해야 합니다.
4. 성공을 확인한 즉시 두 변수를 제거하고 평소 prod 설정으로 다시 기동합니다. 애플리케이션 기본 설정에는 `baseline-on-migrate`를 켜 두지 않습니다.

> 위 baseline 절차를 스키마가 불완전하거나 출처를 모르는 DB에 쓰면 V1을 실행한 것처럼 기록해 버립니다. 새 홈서버 DB처럼 빈 DB에는 baseline 변수를 주지 않고 Flyway가 V1을 직접 적용하게 합니다.

### 배포 이미지 계약

컨테이너 이미지는 GHCR 로 발행하고, 소비자(홈서버 등)는 아래 reference 를 **커밋 SHA 로 pin** 해서 씁니다.

```
ghcr.io/yeonji-p/sensor-monitor-backend:<git-sha>
ghcr.io/yeonji-p/sensor-monitor-explain:<git-sha>
ghcr.io/yeonji-p/sensor-monitor-simulator:<git-sha>
```

- **`latest` 는 발행하지 않습니다.** 같은 태그가 다른 코드를 가리키면 무엇이 돌고 있는지 확인할 수도, 되돌릴 좌표를 잡을 수도 없습니다.
- 발행은 `.github/workflows/publish-images.yml` 의 **수동 실행(workflow_dispatch)** 뿐입니다. main push 는 CI(`ci.yml`, 빌드·테스트)만 돌고 이미지를 덮어쓰지 않습니다.
- 독립 풀 데모가 만드는 이미지는 `sensor-monitor-backend:local`·`sensor-monitor-explain:local`·`sensor-monitor-simulator:local`로, 배포 이미지와 태그가 겹치지 않습니다.
- 최초 발행된 GHCR 패키지는 **private**입니다. workflow의 OCI source 라벨은 이미지 출처와 저장소 연결을 명시할 뿐 visibility를 public으로 바꾸지 않습니다.
- private 유지 시 홈서버가 GHCR 로그인 자격증명을 가져야 합니다. 공개 전환은 발행 후 패키지 설정에서 별도로 결정하며, workflow가 자동으로 바꾸지 않습니다.

---

## 10. 설계 메모

### 메시지 버스 제거

소비자가 하나라 메시지 버스(Kafka)를 두지 않고 수신을 동기 처리(저장, 임계값 판정, 알림 생성)로 했습니다. 다중 소비자가 필요해지면 다시 검토합니다.

### 접근 제어 계층

공장(Factory), 구역(Zone), 구역 소속(ZoneUser) 3계층으로 접근 범위를 계산합니다. `SYSTEM_ADMIN`은 전체, `FACTORY_ADMIN`은 소속 공장, `MEMBER`와 `VIEWER`는 소속 구역으로 범위가 좁혀지며, `VIEWER`는 읽기 전용으로 장치 변경이 차단됩니다.

### freshness 오탐 억제

센서는 정상적으로도 조용해집니다(계획 정지, 비가동, 점검). 침묵을 모두 알림으로 올리면 공장이 문을 닫을 때 장치 수만큼 알림이 쏟아집니다. 그래서 같은 구역 장치가 동시에 침묵하면 사이트 단위 사건으로 보고 한 건으로 묶고(`WARNING`), 이웃은 정상 수신 중인데 혼자 침묵할 때만 개별 고장으로 `CRITICAL` + explain 원인진단을 붙입니다.

### explain 분석 서비스

이상 탐지는 임계값 규칙으로 하고, LLM은 근거 설명과 침묵 원인 진단에만 씁니다. 에이전트 프레임워크 없이 LLM API를 직접 호출합니다. 이 호출은 수신 경로 밖 스케줄러에서만 일어나 수신에 영향을 주지 않습니다. provider는 인터페이스로 분리해 교체할 수 있습니다.

### 수신 모델의 관례와 성능 보류

- alert 종류는 별도 `alert_type` 없이 nullable 참조 조합으로 구분합니다. 임계 alert는 `device_id`·`channel_id`·`batch_id`와 값 스냅샷을 채우고, freshness alert는 `device_id`만 채웁니다. 현재 두 종류뿐이라 컬럼·migration을 늘리지 않으며, 제3의 alert 종류나 타입별 DB 조회·제약이 필요해질 때 명시 판별자를 추가합니다.
- 채널별 reading은 정규화 경계를 유지해 `sensor_reading`과 `measurement_batch`를 join하고 `observed_at`으로 정렬합니다. 조회 상한이 500건인 데모에서는 `sensor_reading.observed_at` 비정규화와 복합 인덱스를 보류하며, 실행 계획이나 부하 측정에서 이 join 정렬이 병목으로 확인되면 추가합니다.
- 수신 엔티티 ID는 기존 `IDENTITY` 전략을 유지합니다. sequence 전환은 batch insert를 가능하게 하지만 migration과 모든 관련 엔티티의 생성 전략을 함께 바꿔야 하므로, 데모 처리량에서는 보류하고 실제 ingest 처리량 목표와 병목 측정이 생길 때 재검토합니다.

### 검토 중

- DeviceType이 Enum 하드코딩이라 타입 추가 시 빌드가 필요합니다. 외부 설정화는 검토 중입니다.
