package dev.bugi.sensor.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLAUDE.md 의 시각 규칙("DB 컬럼은 timestamptz, timestamp without time zone 쓰지 말 것")을 코드로 강제한다.
 *
 * 엔티티의 시각 필드는 Instant 인데 어디에도 columnDefinition 이 없다. 즉 실제 컬럼 타입은
 * Hibernate 의 Instant→SQL 매핑에 전적으로 의존한다. ddl-auto 가 생성한 실 컬럼 타입을
 * information_schema 로 직접 확인해, 매핑이 규칙과 어긋나면 즉시 깨지게 한다.
 *
 * 실측(Hibernate 6.6 / postgres:15): 모든 Instant 필드가 'timestamp with time zone' 으로 생성됨 → 규칙 준수.
 */
class SchemaColumnTypeTest extends AbstractPostgresTest {

    @PersistenceContext
    EntityManager em;

    @ParameterizedTest
    @CsvSource({
            "alert,           created_at",
            "alert,           updated_at",
            "device,          created_at",
            "device,          updated_at",
            "device_status,   last_seen_at",
            "sensor_channel,  created_at",
            "sensor_channel,  updated_at",
            "measurement_batch,observed_at",
            "measurement_batch,received_at",
            "channel_status,  last_alert_at",
            "factories,       created_at",
            "zones,           created_at",
            "zone_users,      created_at",
            "users,           created_at",
            "refresh_tokens,  expires_at",
            "failed_reading,  created_at",
            "factory_operating_calendar, created_at",
            "factory_operating_calendar, updated_at",
    })
    void 모든_Instant_시각컬럼은_timestamptz로_생성된다(String table, String column) {
        String dataType = (String) em.createNativeQuery("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = :t AND column_name = :c
                """)
                .setParameter("t", table.trim())
                .setParameter("c", column.trim())
                .getSingleResult();

        // 'timestamp with time zone' = timestamptz. 'timestamp without time zone' 이면 규칙 위반으로 실패한다.
        assertThat(dataType).isEqualTo("timestamp with time zone");
    }
}
