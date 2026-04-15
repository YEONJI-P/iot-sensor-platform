# GCP 아키텍처 보관 — IoT Sensor Platform

> GCP 구성을 임시 제외하면서 보관하는 파일입니다.
> 재배포 시 이 파일 및 Obsidian의 `GCP 재배포 가이드.md`를 참고하세요.
> 이 파일에는 실제 비밀값(DB_PASSWORD, JWT_SECRET)을 기록하지 않습니다. GitHub Secrets에서 관리합니다.

---

## GCP 리소스 현황

| 항목 | 값 |
|------|-----|
| 프로젝트 ID | project-f4d2acb6-c4a9-4f38-9be |
| 리전 | asia-northeast3 |
| Cloud Run 서비스명 | iot-sensor-platform |
| Artifact Registry 저장소 | iot-docker-repo |
| Artifact Registry 이미지 경로 | asia-northeast3-docker.pkg.dev/project-f4d2acb6-c4a9-4f38-9be/iot-docker-repo/iot-sensor-platform |
| Cloud SQL 인스턴스명 | iot-db-instance |
| Cloud SQL 연결명 | project-f4d2acb6-c4a9-4f38-9be:asia-northeast3:iot-db-instance |
| DB명 | iot_sensor_db_v2 |
| 배포 URL | https://iot-sensor-platform-142990968320.asia-northeast3.run.app |
| Service Account | github-action-pusher@project-f4d2acb6-c4a9-4f38-9be.iam.gserviceaccount.com |
| Workload Identity Provider | projects/142990968320/locations/global/workloadIdentityPools/github-pool/providers/github-provider |

---

## 보관: application-prod.yml

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql:///iot_sensor_db_v2?cloudSqlInstance=project-f4d2acb6-c4a9-4f38-9be:asia-northeast3:iot-db-instance&socketFactory=com.google.cloud.sql.postgres.SocketFactory
    username: postgres
    password: ${DB_PASSWORD}
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update
    show-sql: false
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration

jwt:
  secret-key: ${JWT_SECRET}
  expiration: 3600000

springdoc:
  swagger-ui:
    path: /swagger-ui/index.html
  api-docs:
    path: /api-docs
```

---

## 보관: .github/workflows/ci.yml (GCP 배포 스텝)

```yaml
      - id: 'auth'
        uses: 'google-github-actions/auth@v2'
        with:
          workload_identity_provider: 'projects/142990968320/locations/global/workloadIdentityPools/github-pool/providers/github-provider'
          service_account: 'github-action-pusher@project-f4d2acb6-c4a9-4f38-9be.iam.gserviceaccount.com'

      - name: 'Docker Login'
        run: |
          gcloud auth configure-docker asia-northeast3-docker.pkg.dev

      - name: 'Build and Push'
        run: |
          docker build -t asia-northeast3-docker.pkg.dev/project-f4d2acb6-c4a9-4f38-9be/iot-docker-repo/iot-sensor-platform:latest .
          docker push asia-northeast3-docker.pkg.dev/project-f4d2acb6-c4a9-4f38-9be/iot-docker-repo/iot-sensor-platform:latest

      - name: 'Deploy to Cloud Run'
        run: |
          gcloud run deploy iot-sensor-platform \
            --image asia-northeast3-docker.pkg.dev/project-f4d2acb6-c4a9-4f38-9be/iot-docker-repo/iot-sensor-platform:latest \
            --region asia-northeast3 \
            --platform managed \
            --allow-unauthenticated \
            --set-env-vars "SPRING_PROFILES_ACTIVE=prod,DB_PASSWORD=${{ secrets.DB_PASSWORD }},JWT_SECRET=${{ secrets.JWT_SECRET }}"
```

---

## 보관: build.gradle GCP 의존성

```groovy
implementation 'com.google.cloud.sql:postgres-socket-factory:1.15.1'
```

---

## 보관: Cloud SQL seed.sql 실행 방법

### Cloud SQL Auth Proxy 사용

```bash
# 1. Proxy 기동
cloud-sql-proxy project-f4d2acb6-c4a9-4f38-9be:asia-northeast3:iot-db-instance --port 5433

# 2. seed 실행
psql "host=127.0.0.1 port=5433 dbname=iot_sensor_db_v2 user=postgres" -f iot/seed.sql
```

### gcloud CLI 사용

```bash
gcloud sql connect iot-db-instance --user=postgres --database=iot_sensor_db_v2
# 접속 후: \i iot/seed.sql
```

---

## 보관: GitHub Actions 필요 Secrets

| Secret 이름 | 설명 |
|------------|------|
| `DB_PASSWORD` | Cloud SQL postgres 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (32자 이상) |

> 실제 값은 GitHub 저장소 Settings → Secrets and variables → Actions 에서 확인

---

## 보관: BigQuery 연동 원래 계획 (Day 4)

### 아키텍처 구조

```
기존 구조 (단일 Consumer)
Kafka → iot-sensor-group → SensorData 저장 + Alert 생성 → PostgreSQL

BigQuery 연동 후 구조
Kafka → iot-sensor-group → SensorData 저장 + Alert 생성 → PostgreSQL  (실시간 OLTP)
     → bigquery-group   → BigQuery INSERT               → BigQuery    (대용량 OLAP)
```

### 작업 항목

| 시간 | 작업 | 세부 내용 |
|------|------|---------|
| 1h | BigQuery 데이터셋·테이블 생성 | GCP 콘솔에서 스키마 정의 |
| 1h | 의존성 추가 | `google-cloud-bigquery` 라이브러리 |
| 3h | `BigQueryConsumer` 구현 | `bigquery-group`으로 `sensor-data` 토픽 독립 구독 → BigQuery INSERT |
| 2h | 검증 | 로컬에서 Kafka → 두 Consumer 동시 동작 확인 |
| 1h | README 업데이트 | 아키텍처 다이어그램 반영 |
