package dev.bugi.sensor.sensordata.service;

import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.sensordata.anomaly.AnomalyDetector;
import dev.bugi.sensor.sensordata.dto.SensorDataRequest;
import dev.bugi.sensor.sensordata.entity.SensorData;
import dev.bugi.sensor.sensordata.failure.FailedReading;
import dev.bugi.sensor.sensordata.failure.FailedReadingRepository;
import dev.bugi.sensor.sensordata.repository.SensorDataRepository;
import dev.bugi.sensor.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorDataServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock dev.bugi.sensor.device.repository.DeviceStatusRepository deviceStatusRepository;
    @Mock SensorDataRepository sensorDataRepository;
    @Mock AlertRepository alertRepository;
    @Mock FailedReadingRepository failedReadingRepository;
    @Mock AnomalyDetector anomalyDetector;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock AccessControlService accessControlService; // GET Test
    @Mock UserRepository userRepository; // GET Test
    @Mock Clock clock;

    @Captor ArgumentCaptor<Alert> alertCaptor;

    @InjectMocks
    SensorDataService sensorDataService;

    private static final Instant FIXED = Instant.parse("2026-07-16T00:00:00Z");

    // 가변 시각 홀더. markSeen·발화 등 clock.instant() 호출 횟수와 무관하게
    // 지금 시각을 여기서 통제한다(쿨다운 경과를 테스트에서 재현하려고 값을 옮긴다).
    private final Instant[] now = { FIXED };

    @BeforeEach
    void stubClock() {
        // 수신 실패 경로(장치 없음/무효)에선 clock을 안 쓰므로 lenient.
        lenient().when(clock.instant()).thenAnswer(inv -> now[0]);
    }

    private Device mockDevice(Double threshold) {
        return Device.builder()
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(threshold)
                .build();
    }

    /** 실제 ThresholdDetector와 동일하게 value > threshold 를 흉내낸다(엣지 전이 재현용). */
    private void stubBreachByThreshold(Device device) {
        when(anomalyDetector.isAnomaly(any(Device.class), anyDouble())).thenAnswer(inv -> {
            double v = inv.getArgument(1);
            return device.getThresholdValue() != null && v > device.getThresholdValue();
        });
    }

    private SensorDataRequest req(double value) {
        return new SensorDataRequest(1L, value);
    }

    @Test
    void receive_saves_sensordata() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        sensorDataService.receive(req(50.0));

        verify(sensorDataRepository, times(1)).save(any(SensorData.class));
    }

    @Test
    void receive_normal_value_no_alert() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(anomalyDetector.isAnomaly(any(Device.class), anyDouble())).thenReturn(false);

        sensorDataService.receive(req(50.0));

        verify(alertRepository, never()).save(any());
    }

    @Test
    void receive_device_not_found_records_failure() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        sensorDataService.receive(new SensorDataRequest(99L, 50.0));

        verify(failedReadingRepository, times(1)).save(any(FailedReading.class));
        verify(sensorDataRepository, never()).save(any());
    }

    @Test
    void receive_invalid_request_records_failure() {
        sensorDataService.receive(new SensorDataRequest(null, 50.0));

        verify(failedReadingRepository, times(1)).save(any(FailedReading.class));
        verify(deviceRepository, never()).findById(any());
        verify(sensorDataRepository, never()).save(any());
    }

    // ── 엣지 트리거 쿨다운 ────────────────────────────────────────────

    @Test
    void 첫_초과는_발화하고_알람상태로_전환된다() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0));

        verify(alertRepository, times(1)).save(any(Alert.class));
        assertThat(device.isInAlarm()).isTrue();
        assertThat(device.getLastAlertAt()).isEqualTo(FIXED);
    }

    @Test
    void 연속_초과는_한번만_발화하고_이후_억제된다() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0)); // 발화
        sensorDataService.receive(req(105.0)); // 억제
        sensorDataService.receive(req(110.0)); // 억제

        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    void 여유구간에서는_해제도_재발화도_없다() {
        Device device = mockDevice(80.0); // 해제 경계 = 77.6
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0)); // 발화 → inAlarm
        sensorDataService.receive(req(79.0));  // 여유구간(77.6~80): 초과 아님이나 해제도 안 함
        assertThat(device.isInAlarm()).isTrue();

        sensorDataService.receive(req(100.0)); // 여전히 inAlarm → 억제

        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    void 여유구간_아래로_복귀하고_쿨다운_지나면_재발화한다() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0)); // 발화(FIXED)
        sensorDataService.receive(req(70.0));  // 77.6 미만 → 해제
        assertThat(device.isInAlarm()).isFalse();

        now[0] = FIXED.plus(Duration.ofMinutes(6)); // 쿨다운 5분 경과
        sensorDataService.receive(req(100.0));       // 재발화

        verify(alertRepository, times(2)).save(any(Alert.class));
    }

    @Test
    void 쿨다운_이내_산발스파이크는_재발화하지_않는다() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0)); // 발화(FIXED)
        sensorDataService.receive(req(70.0));  // 해제

        now[0] = FIXED.plus(Duration.ofMinutes(1)); // 쿨다운 5분 이내
        sensorDataService.receive(req(100.0));       // 억제(산발 스파이크 취급)

        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    // ── severity ────────────────────────────────────────────────────

    @Test
    void 임계_1점1배_이하_초과는_WARNING() {
        Device device = mockDevice(80.0); // 88.0 이하면 WARNING
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(85.0));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void 임계_1점1배_초과는_CRITICAL() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }
}
