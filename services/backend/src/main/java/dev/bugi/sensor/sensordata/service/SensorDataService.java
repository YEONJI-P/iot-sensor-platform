package dev.bugi.sensor.sensordata.service;

import dev.bugi.sensor.alert.dto.AlertResponse;
import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.repository.DeviceRepository;
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

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataService {

    private static final int MAX_RECENT = 500; // 장치별 조회 상한

    private final DeviceRepository deviceRepository;
    private final SensorDataRepository sensorDataRepository;
    private final AlertRepository alertRepository;
    private final FailedReadingRepository failedReadingRepository;
    private final AnomalyDetector anomalyDetector;
    private final ApplicationEventPublisher eventPublisher;
    private final AccessControlService accessControlService;
    private final UserRepository userRepository;

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
        device.markSeen(LocalDateTime.now());
        log.info("센서 데이터 저장 완료 - deviceId: {}, value: {}", request.getDeviceId(), request.getValue());
        // 실시간 전송은 커밋 후에 (SseBroadcastListener). 저장 트랜잭션 안에서 I/O를 하지 않는다.
        eventPublisher.publishEvent(
                new SseBroadcastEvent("sensor-data", device.getId(), SensorDataResponse.from(sensorData)));

        if (anomalyDetector.isAnomaly(device, request.getValue())) {
            Alert alert = Alert.builder()
                    .device(device)
                    .sensorValue(request.getValue())
                    .thresholdValue(device.getThresholdValue())
                    .message(String.format("[%s] 임계값 초과! 현재값: %.1f, 임계값: %.1f",
                            device.getName(), request.getValue(), device.getThresholdValue()))
                    .severity(AlertSeverity.WARNING)
                    .build();
            alertRepository.save(alert);
            log.warn("Alert 생성 - device: {}, value: {}", device.getName(), request.getValue());
            eventPublisher.publishEvent(
                    new SseBroadcastEvent("alert", device.getId(), AlertResponse.from(alert)));
        }
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
