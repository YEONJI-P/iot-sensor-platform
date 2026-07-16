package dev.bugi.sensor.sensordata.service;

import dev.bugi.sensor.alert.dto.AlertResponse;
import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.DeviceStatus;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.device.repository.DeviceStatusRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.sensordata.anomaly.AnomalyDetector;
import dev.bugi.sensor.sensordata.dto.SensorDataRequest;
import dev.bugi.sensor.sensordata.dto.SensorDataResponse;
import dev.bugi.sensor.sensordata.entity.SensorData;
import dev.bugi.sensor.sensordata.failure.FailedReading;
import dev.bugi.sensor.sensordata.failure.FailedReadingRepository;
import dev.bugi.sensor.sensordata.repository.SensorDataRepository;
import dev.bugi.sensor.sse.SseBroadcastEvent;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataService {

    private static final int MAX_RECENT = 500; // 장치별 조회 상한

    // 재발화 방지 여유(데드밴드): 임계값의 이 비율 아래로 내려와야 알람 해제.
    // threshold*0.97 ~ threshold 구간에서는 알람을 유지해, 임계값 근처에서 값이 떨려도
    // 껐다 켰다 반복하지 않는다. (양수 threshold 가정)
    private static final double RELEASE_RATIO = 0.97;
    // 이 배율 이하 초과는 WARNING, 초과하면 CRITICAL.
    private static final double CRITICAL_RATIO = 1.1;
    // 재발화 지연(시간 쿨다운): 직전 발화 후 이 시간 안에는 다시 발화하지 않는다.
    // 값이 잠깐 정상 복귀했다 다시 튀는 '산발 스파이크'가 매번 알람이 되는 것을 막는다.
    // 맞바꿈: 이 창 안의 진짜 두 번째 사건도 눌린다(그래서 지나치게 길게 잡지 않는다).
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    private final DeviceRepository deviceRepository;
    private final DeviceStatusRepository deviceStatusRepository;
    private final SensorDataRepository sensorDataRepository;
    private final AlertRepository alertRepository;
    private final FailedReadingRepository failedReadingRepository;
    private final AnomalyDetector anomalyDetector;
    private final ApplicationEventPublisher eventPublisher;
    private final AccessControlService accessControlService;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public void receive(SensorDataRequest request) {
        if (request.getDeviceId() == null || request.getValue() == null) {
            recordFailure(request, "INVALID_REQUEST");
            return;
        }

        Device device = deviceRepository.findById(request.getDeviceId()).orElse(null);
        if (device == null) {
            // 조용히 버리지 않고 실패로 적재한다 (데이터 안 옴 신호 소스)
            recordFailure(request, "DEVICE_NOT_FOUND");
            return;
        }

        SensorData sensorData = SensorData.builder()
                .device(device)
                .value(request.getValue())
                .build();
        sensorDataRepository.save(sensorData);
        markSeen(device);
        log.info("센서 데이터 저장 완료 - deviceId: {}, value: {}", request.getDeviceId(), request.getValue());
        // 실시간 전송은 커밋 후에 (SseBroadcastListener). 저장 트랜잭션 안에서 I/O를 하지 않는다.
        eventPublisher.publishEvent(
                new SseBroadcastEvent("sensor-data", device.getId(), SensorDataResponse.from(sensorData)));

        evaluateAlarm(device, request.getValue());
    }

    /**
     * 엣지 트리거 알람 판정. 임계 초과가 '시작'되는 순간에만 Alert를 만들고,
     * 초과가 이어지는 동안은 억제한다. 값이 재발화 방지 여유 아래로 복귀하면 알람만 해제한다.
     * Device는 트랜잭션 내 관리 엔티티이므로 상태 변경은 dirty check로 저장된다.
     */
    private void evaluateAlarm(Device device, double value) {
        boolean breach = anomalyDetector.isAnomaly(device, value);
        Double threshold = device.getThresholdValue();

        if (breach && !device.isInAlarm()) {
            Instant now = clock.instant();
            // 시간 쿨다운: 직전 발화 후 COOLDOWN 이내면 산발 스파이크로 보고 재발화를 억제한다.
            if (device.getLastAlertAt() != null
                    && Duration.between(device.getLastAlertAt(), now).compareTo(COOLDOWN) < 0) {
                log.debug("Alert 재발화 억제(쿨다운) - device: {}, value: {}", device.getName(), value);
                return;
            }
            // 초과 진입 → 발화. 발화 시점에 크기로 심각도를 정한다.
            AlertSeverity severity = (threshold != null && value <= threshold * CRITICAL_RATIO)
                    ? AlertSeverity.WARNING : AlertSeverity.CRITICAL;
            Alert alert = Alert.builder()
                    .device(device)
                    .sensorValue(value)
                    .thresholdValue(threshold)
                    .message(String.format("[%s] 임계값 초과! 현재값: %.1f, 임계값: %.1f",
                            device.getName(), value, threshold))
                    .severity(severity)
                    .build();
            alertRepository.save(alert);
            device.enterAlarm(now);
            log.warn("Alert 발화 - device: {}, value: {}, severity: {}", device.getName(), value, severity);
            eventPublisher.publishEvent(
                    new SseBroadcastEvent("alert", device.getId(), AlertResponse.from(alert)));
        } else if (!breach && device.isInAlarm()
                && threshold != null && value < threshold * RELEASE_RATIO) {
            // 재발화 방지 여유 아래로 복귀 → 알람 해제(알림 없음).
            device.clearAlarm();
            log.info("Alert 해제 - device: {}, value: {}", device.getName(), value);
        }
        // breach && inAlarm → 억제(생성 안 함) / 여유 구간 → 알람 유지
    }

    // 수신 하트비트는 Device(설정)가 아니라 DeviceStatus(텔레메트리)에 찍는다 — 설정 감사 오염 방지.
    private void markSeen(Device device) {
        Instant now = clock.instant();
        deviceStatusRepository.findById(device.getId())
                .ifPresentOrElse(
                        status -> status.markSeen(now),
                        () -> deviceStatusRepository.save(new DeviceStatus(device, now))
                );
    }

    private void recordFailure(SensorDataRequest request, String reason) {
        failedReadingRepository.save(FailedReading.builder()
                .deviceId(request.getDeviceId())
                .value(request.getValue())
                .reason(reason)
                .build());
        log.warn("수신 실패 적재 - reason: {}, deviceId: {}, value: {}",
                reason, request.getDeviceId(), request.getValue());
    }

    @Transactional(readOnly = true)
    public Page<SensorDataResponse> getAllSensorData(String employeeId, Pageable pageable) {
        User user = getUser(employeeId);
        List<Long> deviceIds = accessControlService.getAccessibleDeviceIds(user);
        if (deviceIds.isEmpty()) return Page.empty(pageable);
        return sensorDataRepository.findByDeviceIdIn(deviceIds, pageable)
                .map(SensorDataResponse::from);
    }

    @Transactional(readOnly = true)
    public List<SensorDataResponse> getAllSensorDataByDeviceId(String employeeId, Long deviceId) {
        User user = getUser(employeeId);
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장치예요 - deviceId: " + deviceId));
        accessControlService.assertCanAccessDevice(user, device);
        // 무제한 로드 방지 — 최근 N건만 반환(응답은 배열 유지, 대시보드 호환).
        return sensorDataRepository
                .findByDeviceIdOrderByRecordedAtDesc(deviceId, PageRequest.of(0, MAX_RECENT))
                .stream().map(SensorDataResponse::from).toList();
    }

    private User getUser(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));
    }
}
