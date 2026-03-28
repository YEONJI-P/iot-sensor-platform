# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### 로컬 개발 환경 시작
```bash
# 의존 서비스 시작 (PostgreSQL + Kafka + Zookeeper)
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

### 빌드
```bash
./gradlew build          # 전체 빌드 (테스트 포함)
./gradlew build -x test  # 테스트 제외 빌드
```

### 테스트
```bash
./gradlew test                                         # 전체 테스트
./gradlew test --tests AuthServiceTest                 # 특정 클래스
./gradlew test --tests AuthServiceTest.signup_success  # 특정 메서드
```

## 아키텍처

### 기술 스택
- **Java 17** + **Spring Boot 3.x** (Web, Data JPA, Security, Validation)
- **PostgreSQL** — 주 데이터베이스 (로컬: docker-compose, 운영: GCP Cloud SQL)
- **Apache Kafka** — 센서 데이터 비동기 처리 (운영 환경에서는 비활성화)
- **JWT** (jjwt) — stateless 인증
- **Swagger/OpenAPI** — `/swagger-ui/index.html` 에서 API 문서 확인

### 도메인 구조
`src/main/java/dev/yeon/iotsensorplatform/` 하위에 6개 도메인:

| 패키지 | 역할 |
|--------|------|
| `auth/` | JWT 발급·검증, Spring Security 필터 설정 |
| `user/` | 회원가입·로그인 처리 |
| `device/` | 센서 장치 CRUD (TEMPERATURE, VIBRATION, ILLUMINANCE) |
| `sensordata/` | 센서 데이터 수신 + Kafka Producer/Consumer |
| `alert/` | 임계값 초과 시 알림 생성·조회 |
| `global/` | 공통 예외 처리, 공유 설정 |

### 데이터 흐름
```
POST /sensor-data
  → SensorDataController
  → SensorDataProducer (Kafka "sensor-data" 토픽 발행)
  → SensorDataConsumer (dual):
      ├─ SensorData 저장 (PostgreSQL)
      └─ 임계값 초과 여부 확인 → Alert 생성
```

### 인증 흐름
모든 요청은 `JwtFilter`를 통과. 공개 엔드포인트:
- `POST /auth/signup`, `POST /auth/login`
- `POST /sensor-data` (장치 → 서버 데이터 전송용)
- `/swagger-ui/**`, `/api-docs/**`

나머지는 JWT Bearer 토큰 필수.

### 프로파일 차이
| 항목 | 로컬 (기본) | 운영 (`prod`) |
|------|------------|--------------|
| DB | `localhost:5432` | GCP Cloud SQL (소켓) |
| Kafka | 활성화 | **비활성화** (KafkaAutoConfiguration exclude) |
| 설정 파일 | `application.yml` | `application-prod.yml` |

### CI/CD
GitHub Actions (`.github/workflows/ci.yml`): Gradle 빌드 → Docker 이미지 → GCP Artifact Registry → Cloud Run 배포. GCP 인증은 Workload Identity Federation 사용 (JSON 키 없음).
