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
import dev.bugi.sensor.sensordata.anomaly.ThresholdDetector;
import dev.bugi.sensor.sensordata.dto.BatchIngestRequest;
import dev.bugi.sensor.sensordata.dto.BatchIngestResult;
import dev.bugi.sensor.sensordata.entity.MeasurementBatch;
import dev.bugi.sensor.sensordata.entity.SensorReading;
import dev.bugi.sensor.sensordata.failure.FailedReading;
import dev.bugi.sensor.sensordata.failure.FailedReadingRepository;
import dev.bugi.sensor.sensordata.repository.MeasurementBatchRepository;
import dev.bugi.sensor.sensordata.repository.SensorReadingRepository;
import dev.bugi.sensor.sse.SseBroadcastEvent;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    @Spy AnomalyDetector anomalyDetector = new ThresholdDetector();
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock AccessControlService accessControlService;
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
        // 상태 일괄 로드: 같은 인스턴스를 돌려줘 receive 호출 간 알람 상태가 이어진다.
        lenient().when(channelStatusRepository.findAllById(any())).thenReturn(List.of(channelStatus));
        lenient().when(deviceStatusRepository.findById(any()))
                .thenReturn(Optional.of(new DeviceStatus(device, FIXED)));
        lenient().when(measurementBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sensorReadingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(channelStatusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(deviceStatusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
        arrange(1416.0);

        sensorDataService.receive(req(1000.0));

        verify(alertRepository, never()).save(any());
    }

    @Test
    void 표시전용_null임계채널은_어떤_값에도_alert를_만들지_않는다() {
        arrange(null);

        sensorDataService.receive(req(-1_000_000.0));
        sensorDataService.receive(req(1_000_000.0));

        verify(alertRepository, never()).save(any());
        assertThat(channelStatus.isInAlarm()).isFalse();
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
        // 거부 판독은 saveAll 로 한 번에 적재한다.
        verify(failedReadingRepository, times(1)).saveAll(any());
        verify(failedReadingRepository, never()).save(any());
    }

    @Test
    void 미지_채널_실패적재에_deviceId가_채워진다() {
        // freshness 원인진단(countByDeviceIdAndCreatedAtAfter)이 세려면 deviceId 가 있어야 한다.
        Device dev = mock(Device.class);
        when(dev.getId()).thenReturn(42L);
        SensorChannel ch = SensorChannel.builder().device(dev).code("s4").unit("°R")
                .quantityKind("temperature").thresholdValue(1416.0)
                .thresholdDirection(ThresholdDirection.ABOVE).build();
        when(deviceRepository.findByCode("CMAPSS-U1")).thenReturn(Optional.of(dev));
        when(sensorChannelRepository.findByDeviceId(42L)).thenReturn(List.of(ch));
        when(measurementBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deviceStatusRepository.findById(any())).thenReturn(Optional.of(new DeviceStatus(dev, FIXED)));
        when(channelStatusRepository.findAllById(any())).thenReturn(List.of());
        when(channelStatusRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Double> m = new LinkedHashMap<>();
        m.put("s4", 100.0);     // known
        m.put("bogus", 5.0);    // unknown
        sensorDataService.receive(new BatchIngestRequest("CMAPSS-U1", null, null, m));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FailedReading>> captor = ArgumentCaptor.forClass(List.class);
        verify(failedReadingRepository).saveAll(captor.capture());
        FailedReading failed = captor.getValue().get(0);
        assertThat(failed.getReason()).isEqualTo("UNKNOWN_CHANNEL");
        assertThat(failed.getChannelCode()).isEqualTo("bogus");
        assertThat(failed.getDeviceCode()).isEqualTo("CMAPSS-U1");
        assertThat(failed.getDeviceId()).isEqualTo(42L);
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
        verify(failedReadingRepository, times(1)).saveAll(any());
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

    @Test
    void 기존_DeviceStatus가_있으면_lastSeenAt이_현재시각으로_갱신된다() {
        arrange(1416.0);
        DeviceStatus existing = new DeviceStatus(device, FIXED.minus(Duration.ofHours(1)));
        when(deviceStatusRepository.findById(any())).thenReturn(Optional.of(existing));

        sensorDataService.receive(req(1000.0));

        assertThat(existing.getLastSeenAt()).isEqualTo(FIXED);
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

        sensorDataService.receive(req(200.0));

        verify(alertRepository, times(1)).save(any(Alert.class));
        assertThat(channelStatus.isInAlarm()).isTrue();
        assertThat(channelStatus.getLastAlertAt()).isEqualTo(FIXED);
    }

    @Test
    void 연속_초과는_한번만_발화하고_이후_억제된다() {
        arrange(100.0);

        sensorDataService.receive(req(200.0)); // 발화
        sensorDataService.receive(req(205.0)); // 억제
        sensorDataService.receive(req(210.0)); // 억제

        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    void 여유구간에서는_해제도_재발화도_없다() {
        arrange(100.0); // 해제 경계 = 97

        sensorDataService.receive(req(200.0)); // 발화 → inAlarm
        sensorDataService.receive(req(99.0));  // 여유구간(97~100): 초과 아님이나 해제도 안 함
        assertThat(channelStatus.isInAlarm()).isTrue();

        sensorDataService.receive(req(200.0)); // 여전히 inAlarm → 억제

        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    void 여유구간_아래로_복귀하고_쿨다운_지나면_재발화한다() {
        arrange(100.0);

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

        sensorDataService.receive(req(105.0));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void 임계_1점1배_초과는_CRITICAL() {
        arrange(100.0);

        sensorDataService.receive(req(200.0));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void severity_경계_정확히_임계1점1배는_WARNING() {
        arrange(100.0); // 100 * 1.1 = 110, value <= 110 이면 WARNING

        sensorDataService.receive(req(110.0));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void severity_경계_임계1점1배_초과는_CRITICAL() {
        arrange(100.0);

        sensorDataService.receive(req(110.01));

        verify(alertRepository).save(alertCaptor.capture());
        assertThat(alertCaptor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    // ── 히스테리시스(해제) 경계 ─────────────────────────────────────────

    @Test
    void 해제경계_정확히_임계0점97배면_알람유지() {
        arrange(100.0); // 100 * 0.97 = 97, 해제 조건은 value < 97 이므로 97 은 유지

        sensorDataService.receive(req(200.0)); // 발화 → inAlarm
        sensorDataService.receive(req(97.0));  // 경계값 → 해제 안 됨

        assertThat(channelStatus.isInAlarm()).isTrue();
    }

    @Test
    void 해제경계_임계0점97배_미만이면_알람해제() {
        arrange(100.0);

        sensorDataService.receive(req(200.0)); // 발화 → inAlarm
        sensorDataService.receive(req(96.99)); // 경계 아래 → 해제

        assertThat(channelStatus.isInAlarm()).isFalse();
    }

    @Test
    void factory_admin_getReadings_same_factory_allowed() {
        User admin = User.builder().employeeId("ADMIN").name("공장 관리자").password("pw")
                .role(Role.FACTORY_ADMIN).status(UserStatus.ACTIVE).build();
        SensorChannel target = SensorChannel.builder().code("s4").thresholdValue(80.0)
                .thresholdDirection(ThresholdDirection.ABOVE).build();
        when(accessControlService.getUser("ADMIN")).thenReturn(admin);
        when(accessControlService.getChannel(1L)).thenReturn(target);
        when(sensorReadingRepository.findByChannelIdOrderByObservedAtDesc(eq(1L), any()))
                .thenReturn(List.of());

        assertThat(sensorDataService.getReadingsByChannel("ADMIN", 1L, 50)).isEmpty();
        verify(accessControlService).assertCanAccessChannel(admin, target);
    }

    @Test
    void factory_admin_getReadings_other_factory_forbidden() {
        User admin = User.builder().employeeId("ADMIN").name("공장 관리자").password("pw")
                .role(Role.FACTORY_ADMIN).status(UserStatus.ACTIVE).build();
        SensorChannel target = SensorChannel.builder().code("s4").build();
        when(accessControlService.getUser("ADMIN")).thenReturn(admin);
        when(accessControlService.getChannel(2L)).thenReturn(target);
        doThrow(new AccessDeniedException("접근 권한이 없는 장치예요"))
                .when(accessControlService).assertCanAccessChannel(admin, target);

        assertThrows(AccessDeniedException.class,
                () -> sensorDataService.getReadingsByChannel("ADMIN", 2L, 50));
        verify(sensorReadingRepository, never()).findByChannelIdOrderByObservedAtDesc(any(), any());
    }
}
