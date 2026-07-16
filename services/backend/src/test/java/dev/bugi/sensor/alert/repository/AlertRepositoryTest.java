package dev.bugi.sensor.alert.repository;

import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.support.AbstractPostgresTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlertRepository 의 두 네이티브 쿼리를 실제 Postgres 로 검증한다.
 * mock 으로는 원리적으로 불가능한 것들: LIMIT 바인딩, 엔티티 매핑, to_char/timestamptz 타임존 변환.
 */
class AlertRepositoryTest extends AbstractPostgresTest {

    @Autowired
    AlertRepository alertRepository;

    @Autowired
    TestEntityManager tem;

    @PersistenceContext
    EntityManager em;

    private Device persistDevice() {
        Factory factory = tem.persist(Factory.builder().name("F1").description(null).build());
        Zone zone = tem.persist(Zone.builder().factory(factory).name("Z1").description(null).build());
        return tem.persist(Device.builder()
                .zone(zone).name("D1").type(Device.DeviceType.TEMPERATURE)
                .location("L1").thresholdValue(80.0).expectedIntervalSeconds(60)
                .build());
    }

    /** created_at 을 명시적으로 지정해 알림 1건을 네이티브 삽입한다(감사 Clock 우회). */
    private void insertAlert(Long deviceId, String message, String severity,
                             String evidence, String recommendation, Instant createdAt) {
        em.createNativeQuery("""
                INSERT INTO alert (device_id, sensor_value, threshold_value, message,
                                   severity, evidence, recommendation, created_at, updated_at)
                VALUES (:deviceId, 99.0, 80.0, :message, :severity, :evidence, :recommendation, :createdAt, :createdAt)
                """)
                .setParameter("deviceId", deviceId)
                .setParameter("message", message)
                .setParameter("severity", severity)
                .setParameter("evidence", evidence)
                .setParameter("recommendation", recommendation)
                .setParameter("createdAt", createdAt)
                .executeUpdate();
    }

    @Test
    void findRecentByDeviceId_limit과_정렬과_신규컬럼_매핑을_검증() {
        Device device = persistDevice();
        Device other = persistDevice();

        Instant base = Instant.parse("2026-01-10T00:00:00Z");
        insertAlert(device.getId(), "old", "WARNING", null, null, base);
        insertAlert(device.getId(), "mid", "CRITICAL", "ev-mid", "rec-mid", base.plusSeconds(60));
        insertAlert(device.getId(), "new", "CRITICAL", "ev-new", "rec-new", base.plusSeconds(120));
        insertAlert(other.getId(), "otherdev", "WARNING", null, null, base.plusSeconds(200));
        em.flush();
        em.clear();

        List<dev.bugi.sensor.alert.entity.Alert> recent =
                alertRepository.findRecentByDeviceId(device.getId(), 2);

        // LIMIT 2 + created_at DESC → [new, mid], other 장치는 제외
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).getMessage()).isEqualTo("new");
        assertThat(recent.get(1).getMessage()).isEqualTo("mid");
        // 엔티티 매핑(신규 severity/evidence/recommendation 컬럼)
        assertThat(recent.get(0).getSeverity()).isEqualTo(dev.bugi.sensor.alert.entity.AlertSeverity.CRITICAL);
        assertThat(recent.get(0).getEvidence()).isEqualTo("ev-new");
        assertThat(recent.get(0).getRecommendation()).isEqualTo("rec-new");
    }

    @Test
    void findDailyCountByDeviceId_to_char는_세션_타임존으로_timestamptz를_변환해_날짜를_가른다() {
        Device device = persistDevice();

        // UTC 자정 경계를 사이에 둔 두 알림.
        Instant beforeMidnightUtc = Instant.parse("2026-01-01T23:30:00Z"); // KST(+9)로는 01-02 08:30
        Instant afterMidnightUtc = Instant.parse("2026-01-02T00:30:00Z");  // KST(+9)로는 01-02 09:30
        insertAlert(device.getId(), "a", "WARNING", null, null, beforeMidnightUtc);
        insertAlert(device.getId(), "b", "WARNING", null, null, afterMidnightUtc);
        em.flush();

        Instant startDate = Instant.parse("2026-01-01T00:00:00Z");

        // 세션 TZ = UTC → 자정 경계 기준으로 두 날짜 버킷(01-01, 01-02)으로 갈린다.
        em.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
        Map<String, Long> utcBuckets = toBuckets(alertRepository.findDailyCountByDeviceId(device.getId(), startDate));
        assertThat(utcBuckets).containsOnlyKeys("2026-01-01", "2026-01-02");
        assertThat(utcBuckets.get("2026-01-01")).isEqualTo(1L);
        assertThat(utcBuckets.get("2026-01-02")).isEqualTo(1L);

        // 세션 TZ = Asia/Seoul(+9) → 두 시각 모두 01-02 로 넘어가 한 버킷(count 2)으로 뭉친다.
        // to_char 가 저장된 timestamptz 를 세션 타임존으로 변환한다는 증거.
        em.createNativeQuery("SET TIME ZONE 'Asia/Seoul'").executeUpdate();
        Map<String, Long> kstBuckets = toBuckets(alertRepository.findDailyCountByDeviceId(device.getId(), startDate));
        assertThat(kstBuckets).containsOnlyKeys("2026-01-02");
        assertThat(kstBuckets.get("2026-01-02")).isEqualTo(2L);
    }

    private Map<String, Long> toBuckets(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                r -> (String) r[0],
                r -> ((Number) r[1]).longValue()));
    }
}
