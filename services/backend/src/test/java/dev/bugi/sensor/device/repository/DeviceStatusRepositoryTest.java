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

    private Device persistDevice(Zone zone, Integer expectedInterval) {
        return tem.persist(Device.builder()
                .zone(zone).name("D").type(Device.DeviceType.TEMPERATURE)
                .location("L").thresholdValue(80.0).expectedIntervalSeconds(expectedInterval)
                .build());
    }

    // ── 검증 3: JOIN FETCH 의미론 — status 행 없는 Device, zone 없는 Device, interval null 은 제외 ──
    @Test
    void findMonitoredWithDeviceAndZone_status행이_있고_interval과_zone이_있는_장치만_조회한다() {
        Zone z = freshZone();

        // (A) 포함: interval 있음 + status 행 있음 + zone 있음
        Device withStatus = persistDevice(z, 60);
        deviceStatusRepository.save(new DeviceStatus(withStatus, Instant.parse("2026-01-01T00:00:00Z")));

        // (B) 제외: interval 있음 + zone 있음이지만 status 행 없음 (수신 이력 없는 장치)
        persistDevice(z, 60);

        // (C) 제외: status 행 있음 + zone 있음이지만 expectedIntervalSeconds = null (미감시)
        Device noInterval = persistDevice(z, null);
        deviceStatusRepository.save(new DeviceStatus(noInterval, Instant.parse("2026-01-01T00:00:00Z")));

        // (D) 제외: interval 있음 + status 행 있음이지만 zone = null (JOIN FETCH d.zone 이 inner join)
        Device noZone = persistDevice(null, 60);
        deviceStatusRepository.save(new DeviceStatus(noZone, Instant.parse("2026-01-01T00:00:00Z")));

        tem.flush();
        tem.clear();

        List<DeviceStatus> result = deviceStatusRepository.findMonitoredWithDeviceAndZone();

        assertThat(result).hasSize(1);
        DeviceStatus only = result.get(0);
        assertThat(only.getDeviceId()).isEqualTo(withStatus.getId());
        // JOIN FETCH 로 device/zone 이 실제 초기화되었는지(트랜잭션 밖 사용 안전) 확인
        assertThat(Hibernate.isInitialized(only.getDevice())).isTrue();
        assertThat(Hibernate.isInitialized(only.getDevice().getZone())).isTrue();
        assertThat(only.getDevice().getZone().getName()).isEqualTo("Z");
    }

    // ── 검증 4: @MapsId 공유 PK — device_id 가 PK 로 공유되는지 ──
    @Test
    void deviceStatus의_PK는_device_id를_공유한다() {
        Zone z = freshZone();
        Device device = persistDevice(z, 60);

        DeviceStatus status = deviceStatusRepository.save(
                new DeviceStatus(device, Instant.parse("2026-01-01T00:00:00Z")));
        tem.flush();

        // @MapsId: 별도 PK 없이 device 의 id 를 그대로 PK 로 쓴다.
        assertThat(status.getDeviceId()).isEqualTo(device.getId());

        Long rowCount = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM device_status WHERE device_id = :id")
                .setParameter("id", device.getId())
                .getSingleResult()).longValue();
        assertThat(rowCount).isEqualTo(1L);
    }

    // ── 검증 4: 같은 device 로 두 번 insert 하면 공유 PK 가 중복을 막는다(장치당 1행) ──
    @Test
    void 같은_device로_DeviceStatus를_두번_persist하면_PK중복으로_실패한다() {
        Zone z = freshZone();
        Device device = persistDevice(z, 60);

        deviceStatusRepository.save(new DeviceStatus(device, Instant.parse("2026-01-01T00:00:00Z")));
        tem.flush();

        // 두 번째 새 인스턴스는 같은 device(=같은 PK)로 INSERT 를 시도 → 중복 PK 위반.
        DeviceStatus dup = new DeviceStatus(device, Instant.parse("2026-01-02T00:00:00Z"));
        assertThatThrownBy(() -> {
            deviceStatusRepository.save(dup);
            tem.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── 검증 4: 올바른 갱신 경로 — 관리 엔티티를 로드해 mutate 하면 같은 행이 갱신된다 ──
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
