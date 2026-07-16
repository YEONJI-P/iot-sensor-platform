# 빌드 스테이지 — 프로젝트 gradlew 래퍼로 동일 Gradle 버전 사용, 테스트 제외 bootJar
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew clean bootJar --no-daemon

# 런타임 스테이지 — JRE 만
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
