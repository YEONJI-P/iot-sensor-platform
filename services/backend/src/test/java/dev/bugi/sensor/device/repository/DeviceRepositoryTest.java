package dev.bugi.sensor.device.repository;

import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FlywayMigrationTest 는 uk_device_code 인덱스의 "존재"만 확인한다. 여기서는 그 제약이
 * 실제로 중복 code 를 거부하는 "동작"까지 실제 Postgres 로 검증한다.
 */
class DeviceRepositoryTest extends AbstractPostgresTest {

    @Autowired
    DeviceRepository deviceRepository;

    private Zone persistZone() {
        return persistZone(persistFactory("F"), "Z");
    }

    // Device 엔티티가 @Table(uniqueConstraints=uk_device_code)로 제약을 미러링하므로
    // (SensorChannel 의 uk_sensor_channel_device_code 미러링과 동일), ddl-auto=create-drop 로
    // 만든 테스트 스키마에도 유니크 제약이 재현되어 중복 code 저장이 거부된다.
    @Test
    void 같은_code로_device를_두번_저장하면_uk_device_code_위반으로_실패한다() {
        Zone zone = persistZone();
        deviceRepository.saveAndFlush(Device.builder()
                .zone(zone).code("DUP-CODE").name("D1").location("L").expectedIntervalSeconds(10).build());

        Device dup = Device.builder()
                .zone(zone).code("DUP-CODE").name("D2").location("L").expectedIntervalSeconds(10).build();
        assertThatThrownBy(() -> deviceRepository.saveAndFlush(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByCode는_등록된_code로_device를_조회한다() {
        Zone zone = persistZone();
        Device saved = deviceRepository.saveAndFlush(Device.builder()
                .zone(zone).code("FIND-ME").name("D").location("L").expectedIntervalSeconds(10).build());
        tem.clear();

        assertThat(deviceRepository.findByCode("FIND-ME")).isPresent()
                .get().extracting(Device::getId).isEqualTo(saved.getId());
        assertThat(deviceRepository.findByCode("NO-SUCH-CODE")).isEmpty();
    }
}
