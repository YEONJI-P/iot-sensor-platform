package dev.bugi.sensor.device.scheduler;

import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.explain.client.ExplainClient;
import dev.bugi.sensor.explain.config.ExplainProperties;
import dev.bugi.sensor.explain.dto.FreshnessDiagnoseRequest;
import dev.bugi.sensor.explain.dto.FreshnessDiagnoseResponse;
import dev.bugi.sensor.factory.calendar.service.OperatingCalendarService;
import dev.bugi.sensor.factory.calendar.service.OperatingCalendarService.OperatingDecision;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.DeviceStatus;
import dev.bugi.sensor.device.repository.DeviceStatusRepository;
import dev.bugi.sensor.sensordata.failure.FailedReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 기대 수신 주기를 넘겨 침묵한 장치를 감지하는 스케줄러.
 *
 * 침묵 = 고장으로 단정하지 않는다. 같은 구역(zone)의 여러 장치가 동시에 침묵하면
 * 개별 센서 고장이 아니라 사이트 단위 사건(계획 정지·게이트웨이/네트워크 장애)일 가능성이 커
 * 구역 1건으로 집계(WARNING)한다. 이웃이 정상 수신 중인데 혼자 침묵하면 개별 고장으로 보고
 * CRITICAL + explain 원인진단을 남긴다. 탐지는 규칙, 원인 설명만 LLM(수신 hot path 밖).
 *
 * 한계: 이웃 없이 혼자 도는 장치가 정상적으로 비가동하는 경우는 여전히 개별 고장으로 잡힌다.
 * 이를 구분하려면 설비 가동상태/운영시간 모델이 필요(향후).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreshnessScheduler {

    // 구역 전체 침묵으로 볼 최소 장치 수(1대뿐이면 비교 대상 이웃이 없어 개별로 처리).
    private static final int COHORT_MIN = 2;

    private final DeviceStatusRepository deviceStatusRepository;
    private final FailedReadingRepository failedReadingRepository;
    private final AlertRepository alertRepository;
    private final ExplainClient explainClient;
    private final ExplainProperties explainProperties;
    private final OperatingCalendarService operatingCalendarService;
    private final Clock clock;

    // 개별 침묵 디바운스: deviceId → 알림을 남긴 시점의 lastSeenAt(회복 시 새 episode).
    private final Map<Long, Instant> diagnosedEpisodes = new ConcurrentHashMap<>();
    // 구역 전체 침묵 디바운스: 연속된 전체 침묵 구간에 1건만.
    private final Set<Long> cohortAlertedZones = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedRateString = "60000")
    public void checkFreshness() {
        // 수신 이력이 있는(status 행 존재) 감시 대상만 조회된다.
        List<DeviceStatus> statuses = deviceStatusRepository.findMonitoredWithDeviceAndZone();
        Instant now = clock.instant();
        Set<Long> factoryIds = statuses.stream()
                .map(status -> status.getDevice().getZone().getFactory().getId())
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, OperatingDecision> decisions = operatingCalendarService.evaluate(factoryIds, now);

        // 관측된 장치를 구역별로, 그중 침묵 장치도 구역별로 모은다.
        Map<Long, List<DeviceStatus>> seenByZone = new HashMap<>();
        Map<Long, List<DeviceStatus>> silentByZone = new HashMap<>();
        for (DeviceStatus status : statuses) {
            Device device = status.getDevice();
            Long factoryId = device.getZone().getFactory().getId();
            OperatingDecision decision = decisions.getOrDefault(factoryId,
                    operatingCalendarService.evaluateAlwaysOpenFallback());
            if (decision.monitoringSuppressed(status.getLastSeenAt())) {
                diagnosedEpisodes.remove(device.getId());
                cohortAlertedZones.remove(device.getZone().getId());
                continue;
            }
            if (status.getLastSeenAt() == null) {
                continue;
            }
            Long zoneId = device.getZone().getId();
            seenByZone.computeIfAbsent(zoneId, k -> new ArrayList<>()).add(status);
            long elapsed = Duration.between(status.getLastSeenAt(), now).getSeconds();
            if (elapsed > device.getExpectedIntervalSeconds()) {
                silentByZone.computeIfAbsent(zoneId, k -> new ArrayList<>()).add(status);
            }
        }

        // 이번 틱에 "구역 전체 침묵"(관측 장치 2대 이상 전부 침묵)인 구역.
        Set<Long> fullySilentZones = new HashSet<>();
        for (Map.Entry<Long, List<DeviceStatus>> e : silentByZone.entrySet()) {
            int silent = e.getValue().size();
            int seen = seenByZone.get(e.getKey()).size();
            if (silent >= COHORT_MIN && silent == seen) {
                fullySilentZones.add(e.getKey());
            }
        }
        // 전체 침묵이 풀린 구역은 코호트 디바운스에서 해제(다음 전체 침묵 때 재알림).
        cohortAlertedZones.retainAll(fullySilentZones);

        for (Map.Entry<Long, List<DeviceStatus>> e : silentByZone.entrySet()) {
            Long zoneId = e.getKey();
            List<DeviceStatus> silent = e.getValue();
            if (fullySilentZones.contains(zoneId)) {
                if (cohortAlertedZones.add(zoneId)) {
                    handleCohortSilence(silent, now);
                }
            } else {
                for (DeviceStatus status : silent) {
                    handleIndividualSilence(status, now);
                }
            }
        }
    }

    // 구역 전체 동시 침묵 — 계획 정지/게이트웨이 장애 가능성. 대표 장치에 집계 1건(WARNING).
    private void handleCohortSilence(List<DeviceStatus> silentStatuses, Instant now) {
        DeviceStatus repStatus = silentStatuses.get(0);
        Device rep = repStatus.getDevice();
        String zoneName = rep.getZone().getName();
        long elapsed = Duration.between(repStatus.getLastSeenAt(), now).getSeconds();
        log.warn("구역 전체 수신 끊김 - zone={}, 장치 {}대 동시 침묵", zoneName, silentStatuses.size());
        Alert alert = Alert.builder()
                .device(rep)
                .message(String.format("구역 전체 수신 끊김 - %s (%d대 동시 침묵, 경과 %ds) · 계획 정지/게이트웨이 장애 가능성",
                        zoneName, silentStatuses.size(), elapsed))
                .severity(AlertSeverity.WARNING)
                .build();
        alertRepository.save(alert);
    }

    // 이웃은 정상인데 혼자 침묵 — 개별 고장으로 보고 explain 원인진단 + CRITICAL.
    private void handleIndividualSilence(DeviceStatus status, Instant now) {
        Device device = status.getDevice();
        Instant lastSeenAt = status.getLastSeenAt();
        if (lastSeenAt.equals(diagnosedEpisodes.get(device.getId()))) {
            return;
        }
        long elapsedSeconds = Duration.between(lastSeenAt, now).getSeconds();
        log.warn("데이터 수신 끊김(개별) - deviceId={}, name={}, elapsed={}s",
                device.getId(), device.getName(), elapsedSeconds);

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
        diagnosedEpisodes.put(device.getId(), lastSeenAt);
    }

    // explain이 꺼져 있거나 호출이 실패해도 알림 자체는 남긴다(진단만 비는 채로).
    private FreshnessDiagnoseResponse diagnose(Device device, Instant lastSeenAt,
                                               long elapsedSeconds, int failedRecent) {
        if (!explainProperties.isEnabled()) {
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
            return explainClient.diagnoseFreshness(request);
        } catch (Exception ex) {
            log.warn("explain freshness 진단 실패 (deviceId={}): {}", device.getId(), ex.getMessage());
            return null;
        }
    }
}
