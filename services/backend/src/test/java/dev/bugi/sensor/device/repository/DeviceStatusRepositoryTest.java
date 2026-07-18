package dev.bugi.sensor.device.repository;

import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.DeviceStatus;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.support.AbstractPostgresTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceStatusRepositoryTest extends AbstractPostgresTest {

    @Autowired
    DeviceStatusRepository deviceStatusRepository;

    @Autowired
    TestEntityManager tem;

    @PersistenceContext
    EntityManager em;

    private Zone freshZone() {
        Factory f = tem.persist(Factory.builder().name("F").description(null).build());
        return tem.persist(Zone.builder().factory(f).name("Z").description(null).build());
    }

    // device.code 는 NOT NULL UNIQUE 라 매번 고유 코드를 부여한다.
    private Device persistDevice(Zone zone, Integer expectedInterval) {
        return tem.persist(Device.builder()
                .zone(zone).code("D-" + UUID.randomUUID()).name("D")
                .location("L").expectedIntervalSeconds(expectedInterval)
                .build());
    }

    // ── JOIN FETCH 의미론 — status 행 없는 Device, zone 없는 Device, interval null 은 제외 ──
    @Test
    void findMonitoredWithDeviceAndZone_status행이_있고_interval과_zone이_있는_장치만_조회한다() {
        Zone z = freshZone();

        Device withStatus = persistDevice(z, 60);
        deviceStatusRepository.save(new DeviceStatus(withStatus, Instant.parse("2026-01-01T00:00:00Z")));

        persistDevice(z, 60); // status 행 없음 → 제외

        Device noInterval = persistDevice(z, null); // interval null → 제외
        deviceStatusRepository.save(new DeviceStatus(noInterval, Instant.parse("2026-01-01T00:00:00Z")));

        Device noZone = persistDevice(null, 60); // zone null → 제외(inner join)
        deviceStatusRepository.save(new DeviceStatus(noZone, Instant.parse("2026-01-01T00:00:00Z")));

        tem.flush();
        tem.clear();

        List<DeviceStatus> result = deviceStatusRepository.findMonitoredWithDeviceAndZone();

        assertThat(result).hasSize(1);
        DeviceStatus only = result.get(0);
        assertThat(only.getDeviceId()).isEqualTo(withStatus.getId());
        assertThat(Hibernate.isInitialized(only.getDevice())).isTrue();
        assertThat(Hibernate.isInitialized(only.getDevice().getZone())).isTrue();
        assertThat(only.getDevice().getZone().getName()).isEqualTo("Z");
    }

    // ── @MapsId 공유 PK — device_id 가 PK 로 공유되는지 ──
    @Test
    void deviceStatus의_PK는_device_id를_공유한다() {
        Zone z = freshZone();
        Device device = persistDevice(z, 60);

        DeviceStatus status = deviceStatusRepository.save(
                new DeviceStatus(device, Instant.parse("2026-01-01T00:00:00Z")));
        tem.flush();

        assertThat(status.getDeviceId()).isEqualTo(device.getId());

        Long rowCount = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM device_status WHERE device_id = :id")
                .setParameter("id", device.getId())
                .getSingleResult()).longValue();
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void 같은_device로_DeviceStatus를_두번_persist하면_PK중복으로_실패한다() {
        Zone z = freshZone();
        Device device = persistDevice(z, 60);

        deviceStatusRepository.save(new DeviceStatus(device, Instant.parse("2026-01-01T00:00:00Z")));
        tem.flush();

        DeviceStatus dup = new DeviceStatus(device, Instant.parse("2026-01-02T00:00:00Z"));
        assertThatThrownBy(() -> {
            deviceStatusRepository.save(dup);
            tem.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 로드후_markSeen하면_같은_행이_제자리_갱신된다() {
        Zone z = freshZone();
        Device device = persistDevice(z, 60);
        deviceStatusRepository.save(new DeviceStatus(device, Instant.parse("2026-01-01T00:00:00Z")));
        tem.flush();
        tem.clear();

        Instant newSeen = Instant.parse("2026-01-05T12:00:00Z");
        DeviceStatus loaded = deviceStatusRepository.findById(device.getId()).orElseThrow();
        loaded.markSeen(newSeen);
        tem.flush();
        tem.clear();

        assertThat(deviceStatusRepository.count()).isEqualTo(1L);
        assertThat(deviceStatusRepository.findById(device.getId()).orElseThrow().getLastSeenAt())
                .isEqualTo(newSeen);
    }
}
