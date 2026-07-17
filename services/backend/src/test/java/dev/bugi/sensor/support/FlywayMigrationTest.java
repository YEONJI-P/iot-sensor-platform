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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 빈 PostgreSQL에 운영(prod) 설정을 그대로 적용한다.
 *
 * 컨텍스트가 뜨려면 Flyway V1/V2 적용 후 Hibernate ddl-auto=validate가 성공해야 한다.
 * 별도 SQL로 history, 핵심 스키마와 공개 데모 토폴로지를 확인해 테스트 설정이 우연히
 * create/update로 우회하지 않았는지도 막는다.
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
    void prod는_flyway_V1_V2를_적용하고_공개_데모_토폴로지를_준비한다() {
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

        var appliedVersions = jdbcTemplate.queryForList("""
                SELECT version FROM flyway_schema_history
                WHERE version IS NOT NULL AND success = true
                ORDER BY installed_rank
                """, String.class);
        assertThat(appliedVersions).containsExactly("1", "2");

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

        assertThat(rowCount("factories")).isEqualTo(2);
        assertThat(rowCount("zones")).isEqualTo(3);
        assertThat(rowCount("device")).isEqualTo(7);
        assertThat(rowCount("users")).isZero();
        assertThat(rowCount("zone_users")).isZero();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM zones z
                JOIN factories f ON f.id = z.factory_id
                WHERE (f.name = '엔진시험동' AND z.name IN ('엔진1구역', '엔진2구역'))
                   OR (f.name = '가공동' AND z.name = '밀링1구역')
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM device d
                JOIN zones z ON z.id = d.zone_id
                WHERE (z.name = '엔진1구역' AND d.name IN ('엔진1-온도(s4)', '엔진1-압력(s11)'))
                   OR (z.name = '엔진2구역' AND d.name IN ('엔진2-온도(s4)', '엔진2-압력(s11)'))
                   OR (z.name = '밀링1구역' AND d.name IN ('CNC1-스핀들파워', 'CNC1-스핀들전류', 'CNC1-X축가속'))
                """, Integer.class)).isEqualTo(7);

        assertThat(jdbcTemplate.queryForList(
                "SELECT name FROM device ORDER BY id", String.class))
                .containsExactly(
                        "엔진1-온도(s4)", "엔진1-압력(s11)",
                        "엔진2-온도(s4)", "엔진2-압력(s11)",
                        "CNC1-스핀들파워", "CNC1-스핀들전류", "CNC1-X축가속"
                );
        assertThat(jdbcTemplate.queryForList(
                "SELECT id FROM device ORDER BY id", Long.class))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM device WHERE expected_interval_seconds = 10", Integer.class))
                .isEqualTo(7);
        assertThat(deviceThresholds()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "엔진1-온도(s4)", 1416.0,
                "엔진1-압력(s11)", 47.8,
                "엔진2-온도(s4)", 1416.0,
                "엔진2-압력(s11)", 47.8,
                "CNC1-스핀들파워", 0.25,
                "CNC1-스핀들전류", 30.0,
                "CNC1-X축가속", 900.0
        ));
    }

    private int rowCount(String tableName) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Integer.class);
    }

    private Map<String, Double> deviceThresholds() {
        return jdbcTemplate.query("SELECT name, threshold_value FROM device", resultSet -> {
            Map<String, Double> thresholds = new HashMap<>();
            while (resultSet.next()) {
                thresholds.put(resultSet.getString("name"), resultSet.getDouble("threshold_value"));
            }
            return thresholds;
        });
    }
}
