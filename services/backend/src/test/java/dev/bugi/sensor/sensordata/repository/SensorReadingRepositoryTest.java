package dev.bugi.sensor.sensordata.repository;

import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.sensordata.entity.MeasurementBatch;
import dev.bugi.sensor.sensordata.entity.SensorReading;
import dev.bugi.sensor.support.AbstractPostgresTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorReadingRepositoryTest extends AbstractPostgresTest {

    @Autowired
    SensorReadingRepository sensorReadingRepository;

    @PersistenceContext
    EntityManager em;

    private SensorChannel persistChannel() {
        Zone z = persistZone(persistFactory("F"), "Z");
        Device d = tem.persist(Device.builder()
                .zone(z).code("D-" + UUID.randomUUID()).name("D").location("L").expectedIntervalSeconds(10).build());
        return tem.persist(SensorChannel.builder()
                .device(d).code("s4").unit("°R").quantityKind("temperature")
                .thresholdValue(80.0).thresholdDirection(ThresholdDirection.ABOVE).build());
    }

    private MeasurementBatch persistBatch(Device device, Instant observedAt) {
        return tem.persist(MeasurementBatch.builder()
                .device(device).observedAt(observedAt).receivedAt(observedAt).sourceSeq(1L).build());
    }

    @Test
    void findByChannelIdOrderByObservedAtDesc_batch관측시각_내림차순으로_반환하고_batch를_초기화한다() {
        SensorChannel channel = persistChannel();
        Device device = channel.getDevice();

        MeasurementBatch b1 = persistBatch(device, Instant.parse("2026-01-01T00:00:00Z"));
        MeasurementBatch b2 = persistBatch(device, Instant.parse("2026-01-02T00:00:00Z"));
        MeasurementBatch b3 = persistBatch(device, Instant.parse("2026-01-03T00:00:00Z"));
        tem.persist(SensorReading.builder().batch(b1).channel(channel).value(10.0).build());
        tem.persist(SensorReading.builder().batch(b2).channel(channel).value(20.0).build());
        tem.persist(SensorReading.builder().batch(b3).channel(channel).value(30.0).build());
        tem.flush();
        tem.clear();

        List<SensorReading> recent = sensorReadingRepository
                .findByChannelIdOrderByObservedAtDesc(channel.getId(), PageRequest.of(0, 2));

        // observed_at DESC + LIMIT 2 → [b3(30), b2(20)]
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).getValue()).isEqualTo(30.0);
        assertThat(recent.get(1).getValue()).isEqualTo(20.0);
        // JOIN FETCH batch → 트랜잭션 밖 접근 안전
        assertThat(Hibernate.isInitialized(recent.get(0).getBatch())).isTrue();
        assertThat(recent.get(0).getBatch().getObservedAt())
                .isEqualTo(Instant.parse("2026-01-03T00:00:00Z"));
    }

    @Test
    void 같은_batch와_channel_조합은_UNIQUE제약으로_중복을_막는다() {
        SensorChannel channel = persistChannel();
        MeasurementBatch batch = persistBatch(channel.getDevice(), Instant.parse("2026-01-01T00:00:00Z"));
        tem.persist(SensorReading.builder().batch(batch).channel(channel).value(10.0).build());
        tem.flush();

        // 리포지토리 프록시로 flush 해 Hibernate 제약 위반을 Spring 예외로 번역받는다.
        SensorReading dup = SensorReading.builder().batch(batch).channel(channel).value(11.0).build();
        assertThatThrownBy(() -> sensorReadingRepository.saveAndFlush(dup))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findLatestByChannelIds는_채널별로_batch조회하고_동률은_reading_id가_큰_행을_선택한다() {
        SensorChannel channel = persistChannel();
        SensorChannel otherChannel = persistChannel();
        Instant tiedAt = Instant.parse("2026-07-16T00:00:00Z");
        MeasurementBatch firstBatch = persistBatch(channel.getDevice(), tiedAt);
        MeasurementBatch secondBatch = persistBatch(channel.getDevice(), tiedAt);
        SensorReading first = tem.persist(SensorReading.builder()
                .batch(firstBatch).channel(channel).value(10.0).build());
        SensorReading second = tem.persist(SensorReading.builder()
                .batch(secondBatch).channel(channel).value(20.0).build());
        MeasurementBatch otherBatch = persistBatch(otherChannel.getDevice(), tiedAt.plusSeconds(10));
        tem.persist(SensorReading.builder()
                .batch(otherBatch).channel(otherChannel).value(30.0).build());
        Long expectedReadingId = second.getId();
        tem.flush();
        tem.clear();

        List<SensorReadingRepository.LatestReadingProjection> latest =
                sensorReadingRepository.findLatestByChannelIds(List.of(channel.getId(), otherChannel.getId()));

        assertThat(latest).hasSize(2);
        SensorReadingRepository.LatestReadingProjection tiedLatest = latest.stream()
                .filter(row -> row.getChannelId().equals(channel.getId()))
                .findFirst().orElseThrow();
        assertThat(tiedLatest.getValue()).isEqualTo(20.0);
        assertThat(tiedLatest.getObservedAt()).isEqualTo(tiedAt);
        assertThat(tiedLatest.getReadingId()).isEqualTo(expectedReadingId);
        assertThat(latest).anySatisfy(row -> {
            assertThat(row.getChannelId()).isEqualTo(otherChannel.getId());
            assertThat(row.getValue()).isEqualTo(30.0);
        });
        assertThat(expectedReadingId).isGreaterThan(first.getId());
    }
}
