package dev.bugi.sensor.device.repository;

import dev.bugi.sensor.device.entity.ChannelStatus;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.support.AbstractPostgresTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChannelStatus 는 DeviceStatus 의 @MapsId 공유 PK 패턴을 복제한다.
 * channel_id 를 PK 로 공유해 채널당 1행을 보장하는지 실제 Postgres 로 검증한다.
 */
class ChannelStatusRepositoryTest extends AbstractPostgresTest {

    @Autowired
    ChannelStatusRepository channelStatusRepository;

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

    @Test
    void channelStatus의_PK는_channel_id를_공유한다() {
        SensorChannel channel = persistChannel();

        ChannelStatus status = channelStatusRepository.save(new ChannelStatus(channel));
        tem.flush();

        assertThat(status.getChannelId()).isEqualTo(channel.getId());
        Long rowCount = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM channel_status WHERE channel_id = :id")
                .setParameter("id", channel.getId())
                .getSingleResult()).longValue();
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void 같은_channel로_두번_persist하면_PK중복으로_실패한다() {
        SensorChannel channel = persistChannel();
        channelStatusRepository.save(new ChannelStatus(channel));
        tem.flush();

        ChannelStatus dup = new ChannelStatus(channel);
        assertThatThrownBy(() -> {
            channelStatusRepository.save(dup);
            tem.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 로드후_enterAlarm하면_같은_행이_제자리_갱신된다() {
        SensorChannel channel = persistChannel();
        channelStatusRepository.save(new ChannelStatus(channel));
        tem.flush();
        tem.clear();

        Instant at = Instant.parse("2026-01-05T12:00:00Z");
        ChannelStatus loaded = channelStatusRepository.findById(channel.getId()).orElseThrow();
        loaded.enterAlarm(at);
        tem.flush();
        tem.clear();

        assertThat(channelStatusRepository.count()).isEqualTo(1L);
        ChannelStatus reloaded = channelStatusRepository.findById(channel.getId()).orElseThrow();
        assertThat(reloaded.isInAlarm()).isTrue();
        assertThat(reloaded.getLastAlertAt()).isEqualTo(at);
    }
}
