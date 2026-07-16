package dev.bugi.sensor.support;

import dev.bugi.sensor.global.config.ClockConfig;
import dev.bugi.sensor.global.config.JpaConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DB 계층 통합 테스트 공용 베이스.
 *
 * 실제 Postgres(프로덕션 docker-compose 의 postgres:15 와 동일 이미지)를 Testcontainers 로 띄워
 * 리포지토리·네이티브 쿼리·제약·컬럼 타입을 검증한다. H2 호환모드로 흉내내지 않는다.
 *
 * 컨테이너는 static 이라 JVM 전체에서 한 번만 뜨고 모든 @DataJpaTest 가 재사용한다
 * (Testcontainers 의 static 컨테이너는 @Testcontainers 가 클래스마다 stop 하지 않는다).
 *
 * 주의:
 * - @DataJpaTest 는 기본적으로 DataSource 를 임베디드 DB 로 바꾸려 하므로 replace = NONE 으로 막는다.
 * - @DataJpaTest 는 @Configuration 을 스캔하지 않으므로, 감사 필드(@CreatedDate)를 채우는
 *   JpaConfig(@EnableJpaAuditing)와 그것이 요구하는 Clock 빈(ClockConfig)을 명시적으로 import 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ClockConfig.class, JpaConfig.class})
@Testcontainers
public abstract class AbstractPostgresTest {

    // 프로덕션(docker-compose.yml)과 동일한 postgres:15.
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("sensor_monitor")
                    .withUsername("sensor_monitor")
                    .withPassword("sensor_monitor");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // 스키마는 ddl-auto 로 매 컨텍스트 생성한다(테스트 컨텍스트는 캐시되어 실질 1회).
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
