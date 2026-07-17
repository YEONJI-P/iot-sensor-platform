# 센서 수신 모델 재검토 handoff

> 상태: 검토 중단·별도 세션으로 이관
> branch: `feat/public-demo-runtime`
> 기존 배포 기준: `main`의 `85b8cbe`

## 중단 이유

공개 홈서버 배포 준비 중 데모 데이터와 simulator를 정리하다가, 현재의 `채널 하나 = Device 하나` 모델이 실제 산업 센서의 동시 관측 묶음을 잃는다는 문제가 제기됐다. 이 변경은 수신 API와 DB의 중심 모델을 바꾸는 별도 설계 작업이므로 홈서버 배포를 더 늦추지 않도록 여기서 중단했다.

이 branch의 `43eb513`은 Flyway V2로 공장 2개·구역 3개·simulator 장치 7개를 넣는다. 아직 어디에도 배포하거나 push하지 않았다. 수신 모델 결정에 따라 V2의 장치 정의도 다시 검토해야 한다.

## 현재 구현

- `Device` 한 건이 물리 장치가 아니라 측정 채널 하나를 나타낸다.
- `POST /sensor-data` 요청은 `deviceId`와 scalar `value` 하나를 받는다.
- `sensor_data`는 `device_id`, `value`, `recorded_at`을 한 행씩 저장한다.
- simulator는 C-MAPSS와 CNC 원본 한 행의 여러 컬럼을 각각 별도 Device로 나눠 독립 HTTP 요청으로 전송한다.
- threshold와 freshness도 채널별 Device를 기준으로 판단한다.
- 이 방식은 README와 `services/simulator/seed.sql`에서 `방식 A(채널=Device)`로 명시한 의도적 단순화다.

원본 C-MAPSS의 한 cycle과 CNC의 한 행은 같은 시점에 관측된 여러 센서 컬럼을 포함하지만, 현재 모델에서는 그 묶음과 동일 시각 관계가 보존되지 않는다.

## 사용자가 설명한 기존 실무 데이터 형태

한 진동 센서 payload에서 `NODE01`, `NODE02` 같은 물리 노드가 함께 오고, 각 노드에는 `x`, `y`, `z` 묶음과 그 아래의 다축 값이 포함됐다. DB에서는 노드별로 `x_x`, `x_y`, `x_z`, `y_x`, `y_y`, `y_z` 같은 컬럼과 같은 관측 시각을 한 행에 저장했다.

핵심 의미는 다음과 같다.

- Device는 개별 scalar 채널보다 물리 노드에 가깝다.
- 한 payload 또는 원본 데이터 한 행의 값들은 하나의 관측 batch다.
- 같은 batch의 채널 값은 같은 `recordedAt`을 공유한다.
- 채널 누락과 부분 실패를 요청 단위에서 구분할 수 있어야 한다.

이 설명은 현재 프로젝트의 확정 요구사항이 아니라, 다음 설계 세션에서 비교할 실제 경험 기반 입력이다.

## 다음 세션에서 비교할 선택지

### A. 현재 단순 모델 유지

- `채널 = Device`, 요청마다 scalar 하나를 유지한다.
- 구현과 대시보드는 단순하지만 동시 관측 묶음을 잃는다.
- 포트폴리오에서는 범용 산업 수집 모델이 아니라 scalar telemetry 데모라고 명확히 제한해야 한다.

### B. 물리 Device와 채널·batch 분리

후보 모델은 다음과 같다.

```text
Device 또는 SensorNode
  └─ SensorChannel

MeasurementBatch(device_id, recorded_at, source_sequence)
  └─ SensorReading(batch_id, channel_id, value)
```

후보 payload는 고정된 진동 전용 wide DTO보다 채널 map 또는 배열을 우선 검토한다.

```json
{
  "deviceCode": "NODE01",
  "recordedAt": "2026-07-17T15:00:00Z",
  "measurements": {
    "x_x": 0.1,
    "x_y": 0.2,
    "x_z": 0.3
  }
}
```

- threshold는 Device가 아니라 SensorChannel 경계로 이동할 가능성이 크다.
- freshness는 물리 Device 또는 batch 수신 경계를 기준으로 판단하는 편이 자연스럽다.
- alert는 어떤 batch와 channel에서 발생했는지 참조해야 한다.
- 원본 payload를 JSONB로 함께 보존할지는 감사·재처리 필요가 확인될 때만 추가한다.
- 진동 전용 wide table은 컬럼이 고정된 제품이라면 효율적이지만, 현재 여러 센서 유형을 다루는 프로젝트에는 normalized channel 모델과 비교가 필요하다.

## 영향 범위

- Flyway V2의 demo Device 7개와 향후 migration
- `SensorDataRequest`, controller, service, entity, repository
- threshold·hysteresis·alert 생성과 freshness 상태 경계
- SSE payload와 dashboard 차트 조회
- simulator의 C-MAPSS cycle/CNC row 변환 및 장치 식별
- 공개 ingest API key 계약과 실패 재시도
- local demo seed와 홈서버 연속 simulator 구성
- 기존 테스트, README ERD와 API 예시

## 완료·중단 상태

완료:

- `43eb513 feat(db): 공개 데모 토폴로지 마이그레이션`
- V2에는 topology만 있고 사용자·권한·비밀번호·pgcrypto는 없다.
- 빈 PostgreSQL에서 Flyway V1/V2와 Hibernate validation을 검증했다.

결정됐지만 미구현:

- 공개 VIEWER는 migration에 알려진 비밀번호로 만들지 않고 opt-in bootstrap으로 분리한다.
- 공개 `POST /sensor-data`는 비밀값으로 주입하는 ingest API key로 보호한다.
- Obsidian 작업 날짜는 Asia/Seoul 오전 5시를 경계로 기록한다.

중단한 구현:

- V3 freshness episode 영속화와 scheduler 시작 유예
- 공개 VIEWER·최초 SYSTEM_ADMIN bootstrap
- local 전용 권한 fixture와 Compose demo 흐름
- 안정적인 장치 식별, synthetic 반복 simulator와 안전한 실패 처리
- simulator SHA 이미지 발행 workflow
- personal-hub의 simulator 서비스 편입

## 재개 순서

1. 현재 API·DB·simulator 흐름을 다시 확인하고 A/B 중 프로젝트 범위를 결정한다.
2. B를 선택하면 먼저 ERD, payload와 호환·migration 전략을 확정한다. 바로 엔티티부터 수정하지 않는다.
3. `43eb513`의 V2 topology를 유지·수정·대체할지 결정한다. 아직 배포 전이므로 적용된 migration으로 가정하지 않는다.
4. 수신 모델을 구현·PostgreSQL에서 검증한 뒤 freshness, bootstrap, ingest 인증과 배포 작업으로 복귀한다.

