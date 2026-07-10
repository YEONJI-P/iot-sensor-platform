# 포트폴리오 대표작 보강 TODO

작성일: 2026-07-02
목적: 이 저장소를 취업 포트폴리오 **대표작(Java/Spring 백엔드)** 으로 내세우기 위한 보강 목록.
출처: 저장소 실측 점검 결과(클론·로컬 `./gradlew test` 실행·CI 이력 확인) 기반.

> 핵심 진단: **"미완성 프로젝트"가 문제가 아니라, README가 완성된 걸 미완성처럼 보이게 하는 게 문제.**
> 라이브 배포는 불필요 — 백엔드는 코드·README·ERD·트러블슈팅을 본다. 1~3번만 해도 미완성 인상이 사라진다.

## 이미 충분한 것 (그대로 내세워도 됨 — 지우지 말 것)
- 실동작 Kafka 파이프라인 (Producer → `@KafkaListener` Consumer → 저장 + 임계값 초과 시 Alert)
- 조직/그룹 3단 접근제어 `AccessControlService` (SUPER_ADMIN → USER_ADMIN → 그룹소속) — **이 프로젝트 최대 차별점**
- JWT Stateless Security + Redis Refresh Token, 승인제 가입(PENDING/ACTIVE/REJECTED)
- 의미 있는 테스트 40개(역할별 접근 허용/차단 검증), `docs/gcp-archive.md` 정직한 배포 회고
- README에 Mermaid 아키텍처 다이어그램 + 설계 트레이드오프 메모 존재

## 보강 우선순위 (대부분 반나절 이내)
- [ ] **1. CI 빨강 복구** (~30분) — `IotSensorPlatformApplicationTests.contextLoads()`가 실제 DB 없어 실패 → CI `gradlew build` exit 1, README 배지 빨강. 조치: 테스트용 `src/test/resources/application.yml`에 H2 추가 또는 해당 테스트에서 DataSource/JPA 오토컨픽 제외(또는 Testcontainers)
- [ ] **2. README "예정 → 완성" 정리** (~1시간) — Redis "(예정)"·시뮬레이터 "삭제 예정"·대시보드 "테스트 중" 표기가 실제 구현보다 뒤처짐. 실제론 다 구현됨 → 완료로 갱신 (완성작을 과소평가 중)
- [ ] **3. README ERD에 조직/그룹 테이블 추가** (~1시간) — 현재 ERD에 `organizations / org_groups / group_users` 누락. 최대 강점(org/group RBAC)이 ERD에서 안 보임
- [ ] **4. 시연 스크린샷/GIF** (~반나절) — `dashboard.html`·Swagger UI 스크린샷, `iot/simulator.py` 실행 터미널 GIF. README 이미지 0장 → 배포 없이 "동작함" 증명
- [ ] **5. `.env.example` 추가** (~20분) — `docker-compose.yml`이 `.env`(POSTGRES_DB/PASSWORD 등) 요구하는데 부재 → `docker-compose up -d` 그대로는 Postgres 안 뜸. `.env.example` + README 실행 절차 완결
- [ ] **6. `test.http` 최신화** (~10분) — 엔드포인트 불일치(`/auth/signup` → 실제 `/auth/register`, email/password 로그인 → 실제 employeeId 기반). 수정 또는 삭제

## 정직성 주의 (제출/면접 전)
- CI 배지 빨강 = 역효과 → 반드시 1번 먼저
- **AI 협업 표기** — 코드 완성도가 높아 "전량 자작"으로 오인될 소지. 이력서/면접에서 "설계·기술선택·검토 주도 + 구현은 AI 병행" 명시
- 면접 대비: **org/group RBAC 설계 이유**, **Kafka 단일 Consumer 선택 근거**를 말로 설명할 수 있으면 방어 충분

## 함께 할 것 (GitHub 프로필)
- [ ] 피처 repo 3~5개 핀 고정, README/커밋 없는 repo는 private 처리 (빈 repo 노출 = 감점)

## 정량 자산 (포폴 "성능" 셀링 강화 — 취업컨설턴트·페르소나 검토 권고)
- [ ] **부하테스트로 처리량(TPS)·p99 지연 수치 확보** — 기존 Python 시뮬레이터로 부하 재현(반나절). 이력서·포폴의 "성능 최적화"를 실측 숫자로 증명. 지금 만들 수 있는 유일한 정량 자산.
- [ ] 사실 기반 규모 지표도 함께 정리 — 디바이스/토픽 수, 테스트 40여 개, 로그 10GB+ 등 before/after

