package dev.bugi.sensor.sensordata.service;

import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.device.entity.ChannelStatus;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.DeviceStatus;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import dev.bugi.sensor.device.repository.ChannelStatusRepository;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.device.repository.DeviceStatusRepository;
import dev.bugi.sensor.device.repository.SensorChannelRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.sensordata.anomaly.AnomalyDetector;
import dev.bugi.sensor.sensordata.dto.BatchIngestRequest;
import dev.bugi.sensor.sensordata.dto.BatchIngestResult;
import dev.bugi.sensor.sensordata.entity.MeasurementBatch;
import dev.bugi.sensor.sensordata.entity.SensorReading;
import dev.bugi.sensor.sensordata.failure.FailedReading;
import dev.bugi.sensor.sensordata.failure.FailedReadingRepository;
import dev.bugi.sensor.sensordata.repository.MeasurementBatchRepository;
import dev.bugi.sensor.sensordata.repository.SensorReadingRepository;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorDataServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock DeviceStatusRepository deviceStatusRepository;
    @Mock SensorChannelRepository sensorChannelRepository;
    @Mock ChannelStatusRepository channelStatusRepository;
    @Mock MeasurementBatchRepository measurementBatchRepository;
    @Mock SensorReadingRepository sensorReadingRepository;
    @Mock AlertRepository alertRepository;
    @Mock FailedReadingRepository failedReadingRepository;
    @Mock AnomalyDetector anomalyDetector;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock AccessControlService accessControlService;
    @Mock UserRepository userRepository;
    @Mock Clock clock;

    @Captor ArgumentCaptor<Alert> alertCaptor;

    @InjectMocks
    SensorDataService sensorDataService;

    private static final Instant FIXED = Instant.parse("2026-07-16T00:00:00Z");

    // 가변 시각 홀더. clock.instant() 호출 횟수와 무관하게 지금 시각을 통제한다(쿨다운 경과 재현용).
    private final Instant[] now = { FIXED };

    private Device device;
    private SensorChannel channel;
    private ChannelStatus channelStatus; // receive 호출 간 알람 상태를 이어가려고 같은 인스턴스를 돌려준다.

    @BeforeEach
    void stubClock() {
        lenient().when(clock.instant()).thenAnswer(inv -> now[0]);
    }

    /** 정상 저장 경로에 필요한 스텁을 세팅한다(테스트별 미사용 가능성 때문에 lenient). */
    private void arrange(Double threshold) {
        device = Device.builder().zone(null).code("CMAPSS-U1").name("엔진 유닛1")
                .location("C-MAPSS unit1").expectedIntervalSeconds(10).build();
        channel = SensorChannel.builder().device(device).code("s4").unit("°R")
                .quantityKind("temperature").thresholdValue(threshold)
                .thresholdDirection(ThresholdDirection.ABOVE).build();
        channelStatus = new ChannelStatus(channel);

        lenient().when(deviceRepository.findByCode("CMAPSS-U1")).thenReturn(Optional.of(device));
        lenient().when(sensorChannelRepository.findByDeviceId(any())).thenReturn(List.of(channel));
        lenient().when(channelStatusRepository.findById(any())).thenReturn(Optional.of(channelStatus));
        lenient().when(deviceStatusRepository.findById(any()))
                .thenReturn(Optional.of(new DeviceStatus(device, FIXED)));
        lenient().when(measurementBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sensorReadingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(channelStatusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(deviceStatusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** 실제 ThresholdDetector(ABOVE)와 동일하게 value > threshold 를 흉내낸다(엣지 전이 재현용). */
    private void stubBreachAbove() {
        lenient().when(anomalyDetector.isAnomaly(any(), anyDouble())).thenAnswer(inv -> {
            SensorChannel c = inv.getArgument(0);
            double v = inv.getArgument(1);
            return c.getThresholdValue() != null && v > c.getThresholdValue();
        });
    }

    private BatchIngestRequest req(double value) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("s4", value);
        return new BatchIngestRequest("CMAPSS-U1", null, null, m);
    }

    private BatchIngestRequest reqMap(Map<String, Double> measurements) {
        return new BatchIngestRequest("CMAPSS-U1", null, null, measurements);
    }

    // ── 저장 · 부분 실패 · 상태코드 ─────────────────────────────────────

    @Test
    void 정상수신은_batch와_reading을_저장하고_SAVED를_반환한다() {
        arrange(1416.0);

        BatchIngestResult result = sensorDataService.receive(req(1000.0));

        assertThat(result.outcome()).isEqualTo(BatchIngestResult.Outcome.SAVED);
        assertThat(result.response().savedCount()).isEqualTo(1);
        assertThat(result.response().rejected()).isEmpty();
        verify(measurementBatchRepository, times(1)).save(any(MeasurementBatch.class));
        verify(sensorReadingRepository, times(1)).save(any(SensorReading.class));
    }

    @Test
    void 정상값은_alert를_만들지_않는다() {
        arrange(1416.0); // isAnomaly 기본 false

        sensorDataService.receive(req(1000.0));

        verify(alertRepository, never()).save(any());
    }

    @Test
    void 장치코드_미존재는_실패적재_1행과_404를_반환하고_batch를_만들지_않는다() {
        when(deviceRepository.findByCode("NOPE")).thenReturn(Optional.empty());
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("s4", 100.0);

        BatchIngestResult result = sensorDataService.receive(new BatchIngestRequest("NOPE", null, null, m));

        assertThat(result.outcome()).isEqualTo(BatchIngestResult.Outcome.DEVICE_NOT_FOUND);
        verify(failedReadingRepository, times(1)).save(any(FailedReading.class));
        verify(measurementBatchRepository, never()).save(any());
    }

    @Test
    void 미지_채널은_거부하고_알려진_채널만_저장한다_200() {
        arrange(1416.0);
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("s4", 1000.0);
        m.put("bogus", 5.0);

        BatchIngestResult result = sensorDataService.receive(reqMap(m));

        assertThat(result.outcome()).isEqualTo(BatchIngestResult.Outcome.SAVED);
        assertThat(result.response().savedCount()).isEqualTo(1);
        assertThat(result.response().rejected()).hasSize(1);
        assertThat(result.response().rejected().get(0).channelCode()).isEqualTo("bogus");
        assertThat(result.response().rejected().get(0).reason()).isEqualTo("UNKNOWN_CHANNEL");
        verify(measurementBatchRepository, times(1)).save(any());
        verify(sensorReadingRepository, times(1)).save(any());
        verify(failedReadingRepository, times(1)).save(any(FailedReading.class));
    }

    @Test
    void 전_채널_미지는_batch미생성_markSeen없음_422() {
        arrange(1416.0);
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("bogus", 5.0);

        BatchIngestResult result = sensorDataService.receive(reqMap(m));

        assertThat(result.outcome()).isEqualTo(BatchIngestResult.Outcome.NO_KNOWN_CHANNELS);
        assertThat(result.response().batchId()).isNull();
        verify(measurementBatchRepository, never()).save(any());
        verify(deviceStatusRepository, never()).findById(any()); // markSeen 안 함
        verify(failedReadingRepository, times(1)).save(any(FailedReading.class));
    }

    @Test
    void null값은_NULL_VALUE로_거부된다() {
        arrange(1416.0);
        Map<String, Double> m = new HashMap<>();
        m.put("s4", null);

        BatchIngestResult result = sensorDataService.receive(reqMap(m));

        assertThat(result.outcome()).isEqualTo(BatchIngestResult.Outcome.NO_KNOWN_CHANNELS);
        assertThat(result.response().rejected()).hasSize(1);
        assertThat(result.response().rejected().get(0).reason()).isEqualTo("NULL_VALUE");
    }

    // ── 하트비트 ────────────────────────────────────────────────────────

    @Test
    void 최초_수신이면_DeviceStatus를_생성해_저장한다() {
        arrange(1416.0);
        when(deviceStatusRepository.findById(any())).thenReturn(Optional.empty());

        sensorDataService.receive(req(1000.0));

        ArgumentCaptor<DeviceStatus> captor = ArgumentCaptor.forClass(DeviceStatus.class);
        verify(deviceStatusRepository).save(captor.capture());
        assertThat(captor.getValue().getDevice()).isSameAs(device);
        assertThat(captor.getValue().getLastSeenAt()).isEqualTo(FIXED);
    }

    // ── SSE 브로드캐스트 ────────────────────────────────────────────────

    @Test
    void 정상수신은_sensordata_이벤트만_발행한다() {
        arrange(1416.0);

        sensorDataService.receive(req(1000.0));

        ArgumentCaptor<SseBroadcastEvent> captor = ArgumentCaptor.forClass(SseBroadcastEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().event()).isEqualTo("sensor-data");
    }

    @Test
    void 발화하면_sensordata와_alert_이벤트를_모두_발행한다() {
        arrange(100.0);
        stubBreachAbove();

        sensorDataService.receive(req(200.0));

        ArgumentCaptor<SseBroadcastEvent> captor = ArgumentCaptor.forClass(SseBroadcastEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(SseBroadcastEvent::event)
                .containsExactly("sensor-data", "alert");
    }

    // ── 엣지 트리거 쿨다운 ──────────────────────────────────────────────

    @Test
    void 첫_초과는_발화하고_알람상태로_전환된다() {
        arrange(100.0);
        stubBreachAbove();

        sensorDataService.receive(req(200.0));

        verify(alertRepository, times(1)).save(any(Alert.class));
        assertThat(channelStatus.isInAlarm()).isTrue();
        assertThat(channelStatus.getLastAlertAt()).isEqualTo(FIXED);
    }

    @Test
    void 연속_초과는_한번만_발화하고_이후_억제된다() {
        arrange(100.0);
        stubBreachAbove();

        sensorDataService.receive(req(200.0)); // 발화
        sensorDataService.receive(req(205.0)); // 억제
        sensorDataService.receive(req(210.0)); // 억제

        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    void 여유구간에서는_해제도_재발화도_없다() {
        arrange(100.0); // 해제 경계 = 97
        stubBreachAbove();

        sensorDataService.receive(req(200.0)); // 발화 → inAlarm
        sensorDataService.receive(req(99.0));  // 여유구간(97~100): 초과 아님이나 해제도 안 함
        assertThat(channelStatus.isInAlarm()).isTrue();

        sensorDataService.receive(req(200.0)); // 여전히 inAlarm → 억제

        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    void 여유구간_아래로_복귀하고_쿨다운_지나면_재발화한다() {
        arrange(100.0);
        stubBreachAbove();

        sensorDataService.receive(req(200.0)); // 발화(FIXED)
        sensorDataService.receive(req(90.0));  // 97 미만 → 해제
        assertThat(channelStatus.isInAlarm()).isFalse();

        now[0] = FIXED.plus(Duration.ofMinutes(6)); // 쿨다운 5분 경과
        sensorDataService.receive(req(200.0));       // 재발화

        verify(alertRepository, times(2)).save(any(Alert.class));
    }

    @Test
    void 쿨다운_이내_산발스파이크는_재발화하지_않는다() {
        arrange(100.0);
        stubBreachAbove();

        sensorDataService.receive(req(200.0)); // 발화(FIXED)
        sensorDataService.receive(req(90.0));  // 해제

        now[0] = FIXED.plus(Duration.ofMinutes(1)); // 쿨다운 5분 이내
        sensorDataService.receive(req(200.0));       // 억제(산발 스파이크 취급)

        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    // ── severity ────────────────────────────────────────────────────────

    @Test
    void 임계_1점1배_이하_초과는_WARNING() {
        arrange(100.0); // 110 이하면 WARNING
        stubBreachAbove();

        sensorDataService.receive(req(105.0));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void 임계_1점1배_초과는_CRITICAL() {
        arrange(100.0);
        stubBreachAbove();

        sensorDataService.receive(req(200.0));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void severity_경계_정확히_임계1점1배는_WARNING() {
        arrange(100.0); // 100 * 1.1 = 110, value <= 110 이면 WARNING
        stubBreachAbove();

        sensorDataService.receive(req(110.0));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void severity_경계_임계1점1배_초과는_CRITICAL() {
        arrange(100.0);
        stubBreachAbove();

        sensorDataService.receive(req(110.01));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    // ── 히스테리시스(해제) 경계 ─────────────────────────────────────────

    @Test
    void 해제경계_정확히_임계0점97배면_알람유지() {
        arrange(100.0); // 100 * 0.97 = 97, 해제 조건은 value < 97 이므로 97 은 유지
        stubBreachAbove();

        sensorDataService.receive(req(200.0)); // 발화 → inAlarm
        sensorDataService.receive(req(97.0));  // 경계값 → 해제 안 됨

        assertThat(channelStatus.isInAlarm()).isTrue();
    }

    @Test
    void 해제경계_임계0점97배_미만이면_알람해제() {
        arrange(100.0);
        stubBreachAbove();

        sensorDataService.receive(req(200.0)); // 발화 → inAlarm
        sensorDataService.receive(req(96.99)); // 경계 아래 → 해제

        assertThat(channelStatus.isInAlarm()).isFalse();
    }
}
