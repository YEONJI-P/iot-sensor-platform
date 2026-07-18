package dev.bugi.sensor.device.repository;

import dev.bugi.sensor.device.entity.ChannelStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelStatusRepository extends JpaRepository<ChannelStatus, Long> {
    // PK 는 channel_id(@MapsId 공유 PK). findById(channelId) 로 상태를 찾는다.
}
