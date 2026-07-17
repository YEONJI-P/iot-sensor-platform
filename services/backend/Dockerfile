# 빌드 스테이지 — 프로젝트 gradlew 래퍼로 동일 Gradle 버전 사용, 테스트 제외 bootJar
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon

# 런타임 스테이지 — JRE 만
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# 이미지 기본값 = 배포 계약. 앱 기본값(프로파일 local, 포트 23100)은 bootRun 개발용이라
# 컨테이너에 그대로 쓰면 안 된다. 배포 측이 아무 env 를 안 줘도 prod/8080 으로 뜨게 고정하고,
# 필요하면 SPRING_PROFILES_ACTIVE·SERVER_PORT 로 재정의한다.
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

# 문서용 메타데이터 — 위 SERVER_PORT 기본값과 같은 값을 유지한다(런타임 매핑은 compose/배포 측 소관).
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
