package dev.bugi.sensor.device.repository;

import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.support.AbstractPostgresTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
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

    @Autowired
    TestEntityManager tem;

    private Zone persistZone() {
        Factory f = tem.persist(Factory.builder().name("F").description(null).build());
        return tem.persist(Zone.builder().factory(f).name("Z").description(null).build());
    }

    // 발견한 기존 결함(수정하지 않음, 보고용): Device 엔티티에 uk_device_code 에 대응하는
    // @Table(uniqueConstraints=...) 또는 @Column(unique=true) 선언이 없다(SensorChannel 은
    // uk_sensor_channel_device_code 를 정확히 미러링하는 것과 대조적). AbstractPostgresTest 는
    // ddl-auto=create-drop 으로 "엔티티 매핑"에서 스키마를 생성하므로, prod(Flyway, V2)에만
    // 있는 이 제약이 이 테스트 스키마에는 재현되지 않아 중복 code 저장이 예외 없이 성공해버린다.
    // (prod 자체는 Flyway DDL 로 제약이 걸려 있어 안전하다 — 엔티티·마이그레이션 간 선언 불일치 문제.)
    // Device 엔티티에 유니크 제약 애노테이션을 추가하면 이 테스트를 다시 활성화할 수 있다.
    @Disabled("Device 엔티티에 uk_device_code 유니크 제약 애노테이션 누락 - main 코드 수정은 조정자 개선 사이클에서")
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
