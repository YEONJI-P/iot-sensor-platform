package dev.bugi.sensor.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 빈 PostgreSQL에 운영(prod) 설정을 그대로 적용한다.
 *
 * 컨텍스트가 뜨려면 Flyway V1 적용 후 Hibernate ddl-auto=validate가 성공해야 한다.
 * 별도 SQL로 history와 핵심 스키마를 확인해 테스트 설정이 우연히 create/update로 우회하지 않았는지도 막는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("prod")
@Testcontainers
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("sensor_monitor")
                    .withUsername("sensor_monitor")
                    .withPassword("sensor_monitor");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Environment environment;

    @Test
    void prod는_flyway_V1을_적용하고_Hibernate가_스키마를_검증한다() {
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

        Integer appliedV1 = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE version = '1' AND success = true
                """, Integer.class);
        assertThat(appliedV1).isEqualTo(1);

        String instantType = jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'sensor_data'
                  AND column_name = 'recorded_at'
                """, String.class);
        assertThat(instantType).isEqualTo("timestamp with time zone");

        Integer sensorIndex = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'sensor_data'
                  AND indexname = 'idx_sensor_device_recorded'
                """, Integer.class);
        assertThat(sensorIndex).isEqualTo(1);
    }
}
