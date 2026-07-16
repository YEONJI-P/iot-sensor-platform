package dev.bugi.sensor.sensordata.service;

import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.DeviceStatus;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.sensordata.anomaly.AnomalyDetector;
import dev.bugi.sensor.sensordata.dto.SensorDataRequest;
import dev.bugi.sensor.sensordata.entity.SensorData;
import dev.bugi.sensor.sensordata.failure.FailedReading;
import dev.bugi.sensor.sensordata.failure.FailedReadingRepository;
import dev.bugi.sensor.sensordata.repository.SensorDataRepository;
import dev.bugi.sensor.sse.SseBroadcastEvent;
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

    // 알람 상태(inAlarm/lastAlertAt)는 Device가 아니라 DeviceStatus에 있으므로,
    // receive 호출 간 상태가 이어지도록 같은 인스턴스를 돌려준다.
    private DeviceStatus status;

    private Device mockDevice(Double threshold) {
        Device device = deviceOnly(threshold);
        status = new DeviceStatus(device, FIXED);
        lenient().when(deviceStatusRepository.findById(any())).thenReturn(Optional.of(status));
        return device;
    }

    // status 스텁 없이 Device 만 만든다(최초 수신·하트비트 경로를 개별 통제하려고).
    private Device deviceOnly(Double threshold) {
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

    // ── DeviceStatus 하트비트(최초 수신 insert · lastSeenAt 갱신) ──────

    @Test
    void 최초_수신이면_DeviceStatus를_생성해_저장한다() {
        // status 행이 아직 없는 장치의 첫 수신 → orElseGet 의 save(new DeviceStatus) 경로.
        Device device = deviceOnly(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceStatusRepository.findById(any())).thenReturn(Optional.empty());
        when(deviceStatusRepository.save(any(DeviceStatus.class))).thenAnswer(inv -> inv.getArgument(0));

        sensorDataService.receive(req(50.0));

        ArgumentCaptor<DeviceStatus> captor = ArgumentCaptor.forClass(DeviceStatus.class);
        verify(deviceStatusRepository).save(captor.capture());
        assertThat(captor.getValue().getDevice()).isSameAs(device);
        assertThat(captor.getValue().getLastSeenAt()).isEqualTo(FIXED);
    }

    @Test
    void 수신하면_lastSeenAt이_현재시각으로_갱신된다() {
        // 기존 status 의 lastSeenAt 을 과거로 두고 수신 → clock.instant() 로 갱신되는지.
        Device device = deviceOnly(80.0);
        DeviceStatus existing = new DeviceStatus(device, FIXED.minus(Duration.ofHours(1)));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceStatusRepository.findById(any())).thenReturn(Optional.of(existing));

        sensorDataService.receive(req(50.0));

        assertThat(existing.getLastSeenAt()).isEqualTo(FIXED);
    }

    // ── SSE 브로드캐스트 발행 ─────────────────────────────────────────

    @Test
    void 정상수신은_sensordata_이벤트만_발행한다() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(anomalyDetector.isAnomaly(any(Device.class), anyDouble())).thenReturn(false);

        sensorDataService.receive(req(50.0));

        ArgumentCaptor<SseBroadcastEvent> captor = ArgumentCaptor.forClass(SseBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().event()).isEqualTo("sensor-data");
    }

    @Test
    void 발화하면_sensordata와_alert_이벤트를_모두_발행한다() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0));

        ArgumentCaptor<SseBroadcastEvent> captor = ArgumentCaptor.forClass(SseBroadcastEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(SseBroadcastEvent::event)
                .containsExactly("sensor-data", "alert");
    }

    // ── 엣지 트리거 쿨다운 ────────────────────────────────────────────

    @Test
    void 첫_초과는_발화하고_알람상태로_전환된다() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0));

        verify(alertRepository, times(1)).save(any(Alert.class));
        assertThat(status.isInAlarm()).isTrue();
        assertThat(status.getLastAlertAt()).isEqualTo(FIXED);
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
        assertThat(status.isInAlarm()).isTrue();

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
        assertThat(status.isInAlarm()).isFalse();

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

    @Test
    void severity_경계_정확히_임계1점1배는_WARNING() {
        // threshold 80 * 1.1 = 88.0, value <= 88.0 이면 WARNING (경계 포함).
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(88.0));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void severity_경계_임계1점1배_초과는_CRITICAL() {
        // 88.0 을 아주 조금만 넘겨도 CRITICAL.
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(88.01));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    // ── 히스테리시스(해제) 경계 ───────────────────────────────────────

    @Test
    void 해제경계_정확히_임계0점97배면_알람유지() {
        // threshold 80 * 0.97 = 77.6, 해제 조건은 value < 77.6 이므로 77.6 은 유지.
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0)); // 발화 → inAlarm
        sensorDataService.receive(req(77.6));  // 경계값 → 해제 안 됨

        assertThat(status.isInAlarm()).isTrue();
    }

    @Test
    void 해제경계_임계0점97배_미만이면_알람해제() {
        Device device = mockDevice(80.0);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        stubBreachByThreshold(device);

        sensorDataService.receive(req(100.0)); // 발화 → inAlarm
        sensorDataService.receive(req(77.59)); // 경계 아래 → 해제

        assertThat(status.isInAlarm()).isFalse();
    }
}
