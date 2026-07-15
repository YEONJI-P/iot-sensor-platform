package dev.yeon.iotsensorplatform.device.scheduler;

import dev.yeon.iotsensorplatform.alert.entity.Alert;
import dev.yeon.iotsensorplatform.alert.entity.AlertSeverity;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.ax.client.AxClient;
import dev.yeon.iotsensorplatform.ax.config.AxProperties;
import dev.yeon.iotsensorplatform.ax.dto.FreshnessDiagnoseRequest;
import dev.yeon.iotsensorplatform.ax.dto.FreshnessDiagnoseResponse;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.sensordata.failure.FailedReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 기대 수신 주기를 넘겨 침묵한 장치를 감지하는 스케줄러.
 * 감지 시 원인진단(규격변경 의심 vs 소스 침묵)을 AX 서비스에 요청해 freshness 알림을 남긴다.
 * 탐지는 규칙(주기 초과), 원인 설명만 LLM. AX 호출은 수신 hot path 밖(스케줄 트리거)이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreshnessScheduler {

    private final DeviceRepository deviceRepository;
    private final FailedReadingRepository failedReadingRepository;
    private final AlertRepository alertRepository;
    private final AxClient axClient;
    private final AxProperties axProperties;

    // 같은 침묵 구간(episode)에 매 분 재알림하지 않도록 하는 디바운스.
    // key=deviceId, value=알림을 남긴 시점의 lastSeenAt. 장치가 회복(lastSeenAt 변경)하면 다음 침묵은 새 episode.
    private final Map<Long, LocalDateTime> diagnosedEpisodes = new ConcurrentHashMap<>();

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
            if (elapsedSeconds <= device.getExpectedIntervalSeconds()) {
                continue;
            }
            // 이번 침묵 episode를 이미 처리했으면 건너뛴다.
            if (lastSeenAt.equals(diagnosedEpisodes.get(device.getId()))) {
                continue;
            }
            log.warn("데이터 수신 끊김 - deviceId={}, name={}, elapsed={}s",
                    device.getId(), device.getName(), elapsedSeconds);
            handleSilence(device, lastSeenAt, elapsedSeconds);
            diagnosedEpisodes.put(device.getId(), lastSeenAt);
        }
    }

    private void handleSilence(Device device, LocalDateTime lastSeenAt, long elapsedSeconds) {
        // 침묵 이후로도 실패 적재가 쌓였는지 = "데이터는 오는데 거부됨" 신호.
        int failedRecent = failedReadingRepository.countByDeviceIdAndCreatedAtAfter(device.getId(), lastSeenAt);

        FreshnessDiagnoseResponse diagnosis = diagnose(device, lastSeenAt, elapsedSeconds, failedRecent);

        Alert alert = Alert.builder()
                .device(device)
                .message(String.format("데이터 수신 끊김 - %s (기대주기 %ds, 경과 %ds)",
                        device.getName(), device.getExpectedIntervalSeconds(), elapsedSeconds))
                .severity(AlertSeverity.CRITICAL)
                .evidence(diagnosis != null ? diagnosis.report() : null)
                .recommendation(diagnosis != null ? diagnosis.cause() : null)
                .build();
        alertRepository.save(alert);
    }

    // AX가 꺼져 있거나 호출이 실패해도 알림 자체는 남긴다(진단만 비는 채로).
    private FreshnessDiagnoseResponse diagnose(Device device, LocalDateTime lastSeenAt,
                                               long elapsedSeconds, int failedRecent) {
        if (!axProperties.isEnabled()) {
            return null;
        }
        FreshnessDiagnoseRequest request = new FreshnessDiagnoseRequest(
                device.getName(),
                device.getExpectedIntervalSeconds(),
                lastSeenAt.toString(),
                (int) elapsedSeconds,
                failedRecent
        );
        try {
            return axClient.diagnoseFreshness(request);
        } catch (Exception e) {
            log.warn("AX freshness 진단 실패 (deviceId={}): {}", device.getId(), e.getMessage());
            return null;
        }
    }
}
