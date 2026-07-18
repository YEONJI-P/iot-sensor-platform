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
 * 컨텍스트가 뜨려면 Flyway V1/V2/V3 적용 후 Hibernate ddl-auto=validate가 성공해야 한다.
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
    void prod는_flyway_V1_V2_V3를_적용하고_공개_데모_토폴로지를_준비한다() {
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

        var appliedVersions = jdbcTemplate.queryForList("""
                SELECT version FROM flyway_schema_history
                WHERE version IS NOT NULL AND success = true
                ORDER BY installed_rank
                """, String.class);
        assertThat(appliedVersions).containsExactly("1", "2", "3");

        // 정규화 batch 스키마: 관측 시각(observed_at)은 batch 가 timestamptz 로 가진다.
        String observedAtType = jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'measurement_batch'
                  AND column_name = 'observed_at'
                """, String.class);
        assertThat(observedAtType).isEqualTo("timestamp with time zone");

        // 구 scalar 텔레메트리 테이블은 제거됐다(호환 레이어 없음).
        assertThat(tableExists("sensor_data")).isFalse();
        assertThat(indexExists("sensor_data", "idx_sensor_device_recorded")).isFalse();

        // 새 정규화 테이블과 인덱스가 생성됐다.
        assertThat(tableExists("sensor_channel")).isTrue();
        assertThat(tableExists("measurement_batch")).isTrue();
        assertThat(tableExists("sensor_reading")).isTrue();
        assertThat(tableExists("channel_status")).isTrue();
        assertThat(indexExists("measurement_batch", "idx_measurement_batch_device_observed")).isTrue();
        assertThat(indexExists("sensor_reading", "idx_sensor_reading_channel")).isTrue();
        assertThat(indexExists("sensor_reading", "uk_sensor_reading_batch_channel")).isTrue();
        assertThat(indexExists("device", "uk_device_code")).isTrue();
        assertThat(indexExists("sensor_channel", "uk_sensor_channel_device_code")).isTrue();

        // device 는 설정만 남겼다: type/threshold_value 제거, code 추가.
        assertThat(columnExists("device", "type")).isFalse();
        assertThat(columnExists("device", "threshold_value")).isFalse();
        assertThat(columnExists("device", "code")).isTrue();
        // 알람 상태는 채널 경계(channel_status)로 이동: device_status 에서 제거.
        assertThat(columnExists("device_status", "in_alarm")).isFalse();
        assertThat(columnExists("device_status", "last_alert_at")).isFalse();
        assertThat(columnExists("device_status", "last_seen_at")).isTrue();
        // alert 앵커: channel_id·batch_id 추가.
        assertThat(columnExists("alert", "channel_id")).isTrue();
        assertThat(columnExists("alert", "batch_id")).isTrue();
        // failed_reading: 문자열 식별자 추가.
        assertThat(columnExists("failed_reading", "device_code")).isTrue();
        assertThat(columnExists("failed_reading", "channel_code")).isTrue();

        assertThat(rowCount("factories")).isEqualTo(2);
        assertThat(rowCount("zones")).isEqualTo(3);
        assertThat(rowCount("device")).isEqualTo(3);
        assertThat(rowCount("sensor_channel")).isEqualTo(7);
        assertThat(rowCount("users")).isZero();
        assertThat(rowCount("zone_users")).isZero();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM zones z
                JOIN factories f ON f.id = z.factory_id
                WHERE (f.name = '엔진시험동' AND z.name IN ('엔진1구역', '엔진2구역'))
                   OR (f.name = '가공동' AND z.name = '밀링1구역')
                """, Integer.class)).isEqualTo(3);

        // 물리 Device 3개(code 기준).
        assertThat(jdbcTemplate.queryForList(
                "SELECT code FROM device ORDER BY id", String.class))
                .containsExactly("CMAPSS-U1", "CMAPSS-U2", "CNC-EXP01");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM device WHERE expected_interval_seconds = 10", Integer.class))
                .isEqualTo(3);

        // 채널 배치: device code → 채널 code 집합.
        assertThat(channelCodes("CMAPSS-U1")).containsExactlyInAnyOrder("s4", "s11");
        assertThat(channelCodes("CMAPSS-U2")).containsExactlyInAnyOrder("s4", "s11");
        assertThat(channelCodes("CNC-EXP01"))
                .containsExactlyInAnyOrder("S1_OutputPower", "S1_CurrentFeedback", "X1_ActualAcceleration");

        // 채널 임계값(device.channel 조합 → threshold).
        assertThat(channelThresholds()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "CMAPSS-U1|s4", 1416.0,
                "CMAPSS-U1|s11", 47.8,
                "CMAPSS-U2|s4", 1416.0,
                "CMAPSS-U2|s11", 47.8,
                "CNC-EXP01|S1_OutputPower", 0.25,
                "CNC-EXP01|S1_CurrentFeedback", 30.0,
                "CNC-EXP01|X1_ActualAcceleration", 900.0
        ));
        // 데모 임계 방향은 전부 ABOVE.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sensor_channel WHERE threshold_direction = 'ABOVE'", Integer.class))
                .isEqualTo(7);
    }

    private int rowCount(String tableName) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Integer.class);
    }

    private boolean tableExists(String table) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """, Integer.class, table) > 0;
    }

    private boolean columnExists(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """, Integer.class, table, column) > 0;
    }

    private boolean indexExists(String table, String index) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = ? AND indexname = ?
                """, Integer.class, table, index) > 0;
    }

    private java.util.List<String> channelCodes(String deviceCode) {
        return jdbcTemplate.queryForList("""
                SELECT c.code FROM sensor_channel c
                JOIN device d ON d.id = c.device_id
                WHERE d.code = ?
                """, String.class, deviceCode);
    }

    private Map<String, Double> channelThresholds() {
        return jdbcTemplate.query("""
                SELECT d.code AS device_code, c.code AS channel_code, c.threshold_value
                FROM sensor_channel c JOIN device d ON d.id = c.device_id
                """, resultSet -> {
            Map<String, Double> thresholds = new HashMap<>();
            while (resultSet.next()) {
                thresholds.put(
                        resultSet.getString("device_code") + "|" + resultSet.getString("channel_code"),
                        resultSet.getDouble("threshold_value"));
            }
            return thresholds;
        });
    }
}
