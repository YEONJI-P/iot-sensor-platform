# IoT Sensor Platform — 코드 재점검 & 수정 계획

작성일: 2026-07-06
방법: README 배제, main 66개 파일(2,333줄) + 테스트 7개 **전량 정독** 후 3개 페르소나 리뷰.
목적: (1) 대표작 코드의 의도 모호·설계 결함 진단, (2) 수정 계획 확정, (3) 이후 AX(AI 에이전트) 확장의 선행조건 정의.
관계: 포폴 **표현** 보강 항목은 `PORTFOLIO_TODO.md`, 이 문서는 **코드·설계** 기준.

---

## 0. 총평

**골격은 대표작감이 맞다. 문제는 간판(README·역할명·주석)이 실제 코드보다 크게 말하고, 그 괴리가 면접에서 드러난다.** 치명 버그는 없으나 이력서 주장(6단계 RBAC·대량 처리·안정적 파이프라인)을 코드가 부분적으로만 방어한다. → 손볼 건 코드 재작성이 아니라 **간판 정직화 + 몇 개 설계 결함 + AX 삽입 지점 마련.**

---

## 1. 3-페르소나 리뷰 결과

### P1. 기술면접관 / 포폴 심사관 — 의도 모호 + 이력서 방어력

| # | 지점 | 근거(file:line) | 문제 |
|---|---|---|---|
| 1 | "6단계 RBAC"가 실제 데이터 권한 3갈래 | `AccessControlService.java:23-67` | 분기는 SYSTEM/ORG/그 외(그룹소속) 3개. DEVICE_MANAGER·DATA_INPUTTER·DATA_ANALYST·VIEWER는 행 접근 **동일**. "6단계"는 URL 레벨 일부 구분(`SecurityConfig:56-66`)뿐 |
| 2 | 주석엔 탈취 감지 상세, 코드엔 없음 | `AuthService.java:85-102` | 주석은 "불일치 시 양쪽 강제 로그아웃+보안 알림+로그"까지 서술. 실제는 `IllegalArgumentException`만. **주석-구현 불일치 = 미완성 신호** |
| 3 | DATA_INPUTTER = 유령 역할 | 전역 / git `ef07b9c` | 원래 사람 수동입력(MANUAL) 경로가 있었고 그때 실체 있었음. MANUAL 제거하며 역할만 고아로 남음 |
| 4 | `/devices` 등록 권한 경계 모호 | `SecurityConfig:69`, `DeviceService.java:29-33` | `/devices`는 매처 없이 `anyRequest().authenticated()` → 그룹 소속이면 VIEWER도 장치 등록 가능. 의도 불명 |
| 5 | 최대 차별점(AccessControlService)에 직접 테스트 0 | `src/test/**` | 타 테스트에서 `@Mock` 처리 → 인가 로직 실제 실행 안 됨. Kafka Consumer도 무테스트 |
| 6 | CI 빨강 | `IotSensorPlatformApplicationTests` | `@SpringBootTest` contextLoads가 실DB·Redis·Kafka 요구 → 인프라 없이 실패 → 배지 red |

### P2. 시니어 백엔드 — 설계 나쁨 / 정확성

| # | 지점 | 근거 | 문제 |
|---|---|---|---|
| 7 | 조회 전량 반환, 페이징 전무 | `AlertService.java:33,42`, `SensorDataService.java:42,52` | 무한 append 데이터를 `findAll...OrderBy...` 전량 로드. **"대량 처리" 주장과 모순** |
| 8 | 시계열 인덱스 없음 | `SensorData.java`, `Alert.java`, `ddl-auto: update` | `(device_id, recorded_at)` 복합 인덱스 미정의 → 정렬·범위 조회 풀스캔 |
| 9 | 이벤트 유실 설계(at-most-once) | `SensorDataConsumer.java:48,51`, `SensorDataProducer.java:24-32` | Consumer는 파싱실패·장치없음 시 조용히 폐기(DLQ·재시도 없음). Producer는 fire-and-forget |
| 10 | 파티션 키 없음 | `SensorDataProducer.java:27` | 키 미지정 → 파티션 2개↑면 장치별 순서 깨짐 |
| 11 | 의미 없는 트랜잭션 + 비원자성 | `SensorDataService.java:28-35` | `receive()`가 `@Transactional`인데 DB 쓰기 없음. Kafka send는 트랜잭션 밖 |
| 12 | 동일 목적 최적화 불일치 | `AccessControlService.java:39 vs 43-44` | SUPER는 `findAllIds()`, ORG 경로는 엔티티 전량 로드 후 `map(getId)` |
| 13 | 잔재 코드 | `SensorData.java:4`(unused import), Producer/Consumer의 미사용 `Profile` import | GCP 프로파일 제거 흔적 |

### P3. 운영·확장 아키텍트 — 보완 / 방향 전환 (AX 준비도)

| # | 지점 | 근거 | 제안 |
|---|---|---|---|
| 14 | 이상 판정이 Consumer에 하드코딩 | `SensorDataConsumer.java:61` (`value > threshold` 인라인) | `AnomalyDetector` 전략 인터페이스로 추출 → threshold 구현체 + (후에) agent 구현체. **AX 핵심 삽입 지점** |
| 15 | Alert 스키마에 근거·심각도 필드 부재 | `Alert.java:24-26` (message 하나) | `severity`·`evidence`·`recommendation` 여지 설계 → AX 때 마이그레이션 최소화 |
| 16 | 무인증 센서 주입 → 위·변조·폭주 | `SecurityConfig:41` | device별 API Key + rate limit. IP allowlist·mTLS는 설계문서에 명시 |
| 17 | 시계열을 OLTP 단일 테이블 적재 | `SensorData` 저장 구조 | 방향(OLAP 분리)은 README 로드맵과 일치. 단기: 인덱스+파티셔닝 |

**의도 모호의 공통 뿌리**: 권한을 Role 하나로 다 표현하려다 **범위(scope) 축과 행위(action) 축이 어긋남**. → 아래 도메인 재정의로 해소.

---

## 2. 확정된 설계 결정 (2026-07-06, 사용자 판단)

> 각 결정의 **근거**를 함께 남긴다 (면접 방어 + 이력서 정직성용).

### 2-1. 포지셔닝: 단일 기업 · 조직 계층 RBAC (멀티테넌트 아님)
- 초기 검토 때 SaaS 멀티테넌트로 프레이밍했으나 **철회**. 근거: `AdminService.getAllUsers()`=`findAll()`, `approveUser`에 조직 체크 없음 → 사용자 관리가 **전사 글로벌**. 멀티테넌트면 누출이지만 **단일 기업이면 정상**. device 접근만 그룹 스코프 = 단일 기업 가정에서 앞뒤가 맞음.
- 서사: "단일 기업 · 조직 계층 기반 RBAC"로 정직하게. (미검증 과장 방지)

### 2-2. 도메인 계층 리네임
```
회사(전체)  = SYSTEM_ADMIN 스코프 (암묵, 단일 기업)
  Factory(공장)   ← 기존 Organization
    Zone(구역)    ← 기존 OrgGroup
      Device(설비)
사람은 Zone에 배정(GroupUser) → 자기 구역 설비만 조회
```
- `Organization` → **`Factory`**, `OrgGroup` → **`Zone`** 리네임 (테이블·FK·repo 포함).
- **Zone(구역) 선택 근거**: "라인(Line)"은 이산 제조 전용. 이 플랫폼의 데이터(RFP 참조: NASA C-MAPSS 엔진·KAMP CNC)는 **라인 없는 개별 설비**라 Line은 부적합. 구역(Zone)은 라인 유무 무관하게 성립.
- 회사/사업장(Site)은 공장 **상위** 레벨이라 현 2단 구조에 미포함 → 다중 사업장 필요 시 그때 상위 추가.

### 2-3. 역할 모델: 6개 → 4개
| Role | 축 | 권한 |
|---|---|---|
| `SYSTEM_ADMIN` | 최상위 | 권한 부여/수정 · 시스템 관리 · 전체 뷰(테스트) |
| `ORG_ADMIN` | 관리 | 전사 멤버 관리(승인·Zone 배정). **센서데이터 조회 안 함**(현 코드대로 GET 제외 유지) |
| `MEMBER` | 행위=쓰기 | 소속 Zone 설비 등록·운영·조회 |
| `VIEWER` | 행위=읽기 | 소속 Zone 조회만. **mutation 403 실구현**(안 막으면 유령 역할됨) |
| ~~device~~ | 비-user | User로 취급 X. API Key로 수신 인증(유저 JWT와 분리) |

- 폐기: `DEVICE_MANAGER`, `DATA_INPUTTER`, `DATA_ANALYST` (행위 구분 없이 중복/고아).
- 팀(Zone) 한정 관리자 role은 **두지 않음**: 일반 사용자가 이미 Zone 단위로 좁혀 보므로 불필요. (다중 공장 규모 커지면 그때 Factory 스코프 배정 추가)

### 2-4. 수신 인증
- **구현**: device/소스별 API Key 헤더 검증 + rate limit (데모 가능).
- **문서화(구현 X)**: 운영 시 IP allowlist(네트워크) + mTLS/AWS IoT Core류(대규모). 면접 답변: "개인 프로젝트라 무인증이나, 실서비스면 고정 IP allowlist + 장치별 API Key 이중, 대규모면 MQTT+mTLS."

---

## 3. 수정 계획 (Tier 순)

### Tier 0 — 거짓·미완성 신호 제거 (AX 무관, 최우선)
- [ ] 0-1. CI 빨강 복구 — `contextLoads`에 H2 또는 오토컨픽 제외 (#6)
- [ ] 0-2. 역할 6→4 축소 — `Role` enum · `SecurityConfig` 매처 · `iot/seed.sql` 7계정 · 테스트 (#1,#3) + VIEWER 쓰기차단 실구현
- [ ] 0-3. 탈취감지 주석↔구현 정합 — 불일치 시 토큰 삭제 최소구현 or 주석 축소 (#2)
- [ ] 0-4. 엔티티 리네임 `Organization→Factory`, `OrgGroup→Zone` (#2-2)
- [ ] 0-5. 잔재 import 정리 (#13)

### Tier 1 — 설계 결함 ("대량 처리" 방어)
- [ ] 1-1. 조회 Pageable화(Alert·SensorData) (#7)
- [ ] 1-2. `(device_id, recorded_at)` 인덱스 (#8)
- [ ] 1-3. `receive()` 불필요 트랜잭션 제거 · AccessControl ID조회 일원화 (#11,#12)
- [ ] 1-4. 수신 API Key + rate limit 구현, IP/mTLS 문서화 (#16)

### Tier 2 — AX 삽입 지점 + 데이터 아키텍처 (AX 선행 필수)
- [ ] 2-1. 이상감지 로직 `AnomalyDetector` 전략 추출 (#14)
- [ ] 2-2. `Alert` 스키마에 severity·evidence·recommendation 여지 (#15)
- [ ] 2-3. **#9 silent-drop 수정 → 파싱/검증 실패를 DLQ/실패테이블로 적재** (버그 수정이자 §6 스파인②의 데이터 소스 — 격상)
- [ ] 2-4. 버스 교체 Kafka→Redis fan-out (store/ax/olap/alarm) — §5-1 참조
- [ ] 2-5. 대시보드 폴링 → SSE push (§5-2)
- [ ] 2-6. 조회 경로 OLTP/OLAP(또는 TimescaleDB) 분리 설계 — §6 스파인③
- [ ] 2-7. `Device.expectedIntervalSeconds`·`lastSeenAt` + freshness 감지 — §6 스파인②

### Tier 3 — 방어 증거
- [ ] 3-1. `AccessControlService`·Kafka Consumer 직접 테스트 (#5)

---

## 4. AX 백로그 (Tier 2 완료 후 착수)

포지셔닝 스파인(§6) 3축을 기능으로 구현. 셋 다 실무 통증에서 출발.

**스파인① 데이터가 이상함 — 이상탐지 + 에이전트 근거** (RFP-제조-실시간모니터링 확장요구 2.2 충족)
- [ ] 멀티에이전트 근거·조치 권고 생성 (감지 에이전트 + 검토·권고 에이전트, Python/FastAPI)

**스파인② 데이터가 안 옴 — freshness 감지 + 원인진단 에이전트** ★차별점 (실무 규격변경 사고 근거)
- [ ] freshness 탐지(기대주기 초과) = 규칙/heartbeat
- [ ] DLQ 유무로 "규격변경 의심 vs 소스 침묵" 1차 갈림 = 규칙
- [ ] 에이전트가 신호 종합해 원인 추정 + 조치 리포트 = AI (신호 모호할 때 값)
- [ ] (v2) Zone/Factory 가동 캘린더로 "예정 비가동" 판별
- 정직: **탐지=규칙, 진단=에이전트**로 구분 표기. 전체를 "AI"라 하지 않음

**스파인③ 데이터가 너무 많음 — 수집/조회 분리** (실무 진동센서 통증)
- [ ] OLTP(수집) / OLAP·TimescaleDB(조회·분석) 분리, fan-out olap-group으로 적재
- [ ] (문서) BigQuery는 클라우드 스케일 버전으로 언급

**공통**
- [ ] device 추가 → 시뮬레이터 프로필 자동 생성 (AI는 "이상 시나리오 생성"에만, 값범위는 템플릿 — 과장 방지)
- [ ] seed 장치를 C-MAPSS 엔진 100대로 교체(실데이터 데모)

> **분리 주의**: 이 IoT-AX(개인, 제조 도메인)는 KT AX 2차 팀플(팀장, ICT 로그장애분석 RFP)과 **별개 산출물**. 스파인②는 개념상 RCA로 2차와 사촌이나 도메인·역할이 달라 provenance 분리. 증거등급도 다름(개인 "구현했다" vs 부캠 "다뤄봤다").

---

## 5. 아키텍처 결정 (2026-07-06) — "왜 이 선택?" 면접 답변 원본

### 5-1. 이벤트 버스: Kafka 제거 → Redis fan-out
- **버스의 목적 = fan-out(소비자 분리)이지 throughput 아님.** 저장·AX(Python)·OLAP·알림이 같은 이벤트를 독립 소비. AX가 별도 프로세스(Python)라 강결합 회피 위해 버스 필요.
- **Kafka 제거 근거**: 현 규모(수백 msg/s급)엔 과함. 소비자 1개뿐이라 fan-out 미실현, partition·ack 이점 없음. partition은 throughput 병렬화용 → 이 규모엔 불필요(partition 1이면 key/ack 튜닝 자체가 없음).
- **선택 = Redis fan-out**: 이미 Redis(리프레시 토큰) 사용 → 인프라 통합(ZK+Kafka 2개 제거). 구현은 **Pub/Sub부터**(개념 최소), OLAP 유실 방지 필요 시 그 소비자만 **Streams**로 업글(점진).
- **Kafka 확장 시나리오는 문서로만**: "처리량 N배 시 partition 늘리고 key=deviceId로 장치별 순서 유지하며 병렬 소비" — 개념 이해 증명, 구현 유보.
- **수신 진입 = HTTP POST 유지**(Spring이 API Key 인증·검증 후 버스로). MQTT는 "실 IoT면" 선택지로 문서화만.

### 5-2. 대시보드: 30초 폴링 → SSE push
- 서버→클라 단방향 → **SSE**가 WebSocket보다 가볍고 정확. HTTP 네이티브·자동재연결.
- RFP 실시간모니터링 확장요구(2.2 push·자동재연결) 그대로 충족.
- 한계 인지: 다중 인스턴스 스케일아웃 시 emitter 공유(Redis Pub/Sub·sticky) 필요. 개인=단일 인스턴스라 무관.

## 6. 프로젝트 포지셔닝 — 실경험 기반 데이터 플랫폼

buzzword 나열(IoT+Kafka+JWT)은 차별화 0. **실무 통증 3개를 구조로 푸는 것**이 스파인.

| 스파인 | 실무 통증(겪음) | 개인작 해결(구현) |
|---|---|---|
| ① 데이터 이상 | — | 이상탐지 + 에이전트 근거 |
| ② 데이터 안 옴 ★ | 협력사 규격변경으로 데이터 조용히 유실, 뒤늦게 log 뒤져 발견 | freshness 감지 + 원인진단 에이전트 |
| ③ 데이터 많음 | 진동센서 대량 → DB 비대·조회 병목 | 수집/조회 분리(OLTP/OLAP·TimescaleDB) |

- **한 줄**: "제조 센서 시계열의 수집·조회를 분리해 대량 데이터 병목을 구조로 푸는 백엔드 — 실무에서 겪은 진동센서 조회·데이터 유실 문제에서 출발", 그 위에 이상탐지·AX.
- **§1 부합**: 셋 다 "경력에서 느낀 필요 → 스스로 메움"(career §1). 실무가 중심.
- **provenance 정직**: 실무는 "겪었다"(문제 경험), 개인작은 "설계·구현했다". "실무에서 OLAP로 개선했다"는 **금지**(안 함).
- **차별점 핵심 = ②**. silent-drop(#9)이 네 실사고의 원인이자 이 프로젝트의 현재 버그 → 고치면 "내 사고를 잡는 시스템"이 되는 폐루프.

## 7. 면접 방어 노트 (정직성 기준)

- **RBAC**: "단일 기업 조직 계층(Factory→Zone) 기반 행 수준 접근제어. 앱 계층 수동 격리라 체크 누락 시 누출 위험을 인지." (멀티테넌시 용어는 개념 설명 시만, 구현은 이 수준으로 정확히)
- **AI 협업**: 설계·기술선택·검토 주도 + 구현 AI 병행 명시. "Spring 구현 숙련"으로 과장 X, "설계·의사결정 오너십"으로.
- **수신 인증**: §2-4 답변(개인=무인증/API Key, 실서비스=IP allowlist+API Key, 대규모=MQTT+mTLS).
- **Kafka 정직 귀속**: 실무 = "타사 공유 데이터 파이프라인에서 Kafka **운영·활용**"(상사 구축, 본인 오너 아님 → "구축" X). 개인작 = "규모에 맞게 경량 Redis fan-out **직접 설계**". → 키워드+판단력+정직 동시.
- **버스/감지로직 분리 근거**(§5-1)를 말로 설명 가능하면 방어 충분.
