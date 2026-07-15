package dev.yeon.iotsensorplatform.device.scheduler;

import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreshnessScheduler {

    private final DeviceRepository deviceRepository;

    @Scheduled(fixedRateString = "60000")
    public void checkFreshness() {
        List<Device> devices = deviceRepository.findByExpectedIntervalSecondsIsNotNull();
        LocalDateTime now = LocalDateTime.now();

        for (Device device : devices) {
            LocalDateTime lastSeenAt = device.getLastSeenAt();
            if (lastSeenAt == null) {
                continue;
            }
            long elapsedSeconds = Duration.between(lastSeenAt, now).getSeconds();
            if (elapsedSeconds > device.getExpectedIntervalSeconds()) {
                log.warn("데이터 수신 끊김 - deviceId={}, name={}", device.getId(), device.getName());
            }
        }
    }
}
