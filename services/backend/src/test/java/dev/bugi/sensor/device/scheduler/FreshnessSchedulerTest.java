package dev.bugi.sensor.device.scheduler;

import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.ax.client.AxClient;
import dev.bugi.sensor.ax.config.AxProperties;
import dev.bugi.sensor.ax.dto.FreshnessDiagnoseRequest;
import dev.bugi.sensor.ax.dto.FreshnessDiagnoseResponse;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.DeviceStatus;
import dev.bugi.sensor.device.repository.DeviceStatusRepository;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.sensordata.failure.FailedReadingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreshnessSchedulerTest {

    @Mock DeviceStatusRepository deviceStatusRepository;
    @Mock FailedReadingRepository failedReadingRepository;
    @Mock AlertRepository alertRepository;
    @Mock AxClient axClient;
    @Mock AxProperties axProperties;
    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    @Spy Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks FreshnessScheduler scheduler;

    private DeviceStatus device(long id, long zoneId, long silentSeconds) {
        Zone zone = mock(Zone.class);
        when(zone.getId()).thenReturn(zoneId);
        when(zone.getName()).thenReturn("Z" + zoneId);
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(id);
        when(device.getName()).thenReturn("dev" + id);
        when(device.getZone()).thenReturn(zone);
        when(device.getExpectedIntervalSeconds()).thenReturn(10);
        DeviceStatus status = mock(DeviceStatus.class);
        when(status.getDevice()).thenReturn(device);
        when(status.getLastSeenAt()).thenReturn(NOW.minusSeconds(silentSeconds));
        return status;
    }

    private DeviceStatus silent(long id, long zoneId) { return device(id, zoneId, 120); }
    private DeviceStatus healthy(long id, long zoneId) { return device(id, zoneId, 2); }

    @Test
    void 혼자_침묵하면_AX진단이_담긴_CRITICAL() {
        DeviceStatus d1 = silent(1, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1));
        when(axProperties.isEnabled()).thenReturn(true);
        when(failedReadingRepository.countByDeviceIdAndCreatedAtAfter(eq(1L), any())).thenReturn(0);
        when(axClient.diagnoseFreshness(any(FreshnessDiagnoseRequest.class)))
                .thenReturn(new FreshnessDiagnoseResponse("소스 침묵 의심", "수신 자체가 끊긴 것으로 보임", "echo"));

        scheduler.checkFreshness();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert alert = captor.getValue();
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alert.getEvidence()).isEqualTo("수신 자체가 끊긴 것으로 보임");
        assertThat(alert.getRecommendation()).isEqualTo("소스 침묵 의심");
    }

    @Test
    void 이웃은_정상인데_혼자_침묵하면_개별_CRITICAL() {
        // 같은 구역에 정상 수신 중인 이웃이 있으므로 게이트웨이·사이트는 정상 → 개별 고장.
        DeviceStatus d1 = silent(1, 100), d2 = healthy(2, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1, d2));
        when(axProperties.isEnabled()).thenReturn(true);
        when(axClient.diagnoseFreshness(any())).thenReturn(new FreshnessDiagnoseResponse("c", "r", "echo"));

        scheduler.checkFreshness();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        verify(axClient).diagnoseFreshness(any());
    }

    @Test
    void 구역_전체가_동시_침묵하면_WARNING_집계_1건_AX호출없음() {
        // 두 장치 모두 침묵 = 계획정지/게이트웨이 가능성 → 장치별 CRITICAL 대신 구역 1건.
        DeviceStatus d1 = silent(1, 100), d2 = silent(2, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1, d2));

        scheduler.checkFreshness();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(captor.getValue().getMessage()).contains("구역 전체");
        verify(axClient, never()).diagnoseFreshness(any());
    }

    @Test
    void 구역_전체_침묵은_틱마다_재알림하지_않는다() {
        DeviceStatus d1 = silent(1, 100), d2 = silent(2, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1, d2));

        scheduler.checkFreshness();
        scheduler.checkFreshness();

        verify(alertRepository, times(1)).save(any());
    }

    @Test
    void 개별_침묵은_틱마다_재알림하지_않는다() {
        DeviceStatus d1 = silent(1, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1));
        when(axProperties.isEnabled()).thenReturn(true);
        when(axClient.diagnoseFreshness(any())).thenReturn(new FreshnessDiagnoseResponse("c", "r", "echo"));

        scheduler.checkFreshness();
        scheduler.checkFreshness();

        verify(alertRepository, times(1)).save(any());
    }

    @Test
    void AX비활성이면_진단없이_알림만() {
        DeviceStatus d1 = silent(1, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1));
        when(axProperties.isEnabled()).thenReturn(false);

        scheduler.checkFreshness();

        verify(axClient, never()).diagnoseFreshness(any());
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getEvidence()).isNull();
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void 기대주기_이내면_알림없음() {
        DeviceStatus d1 = healthy(1, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1));

        scheduler.checkFreshness();

        verifyNoInteractions(alertRepository, axClient);
    }

    @Test
    void 수신시각_없는_상태행은_건너뛴다() {
        // 한 번도 수신 없는 장치는 device_status 행 자체가 없어 조회(JOIN)에서 빠진다.
        // 그래도 lastSeenAt 이 비어 있는 행은 방어적으로 건너뛴다.
        DeviceStatus neverSeen = mock(DeviceStatus.class);
        when(neverSeen.getLastSeenAt()).thenReturn(null);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(neverSeen));

        scheduler.checkFreshness();

        verifyNoInteractions(alertRepository, axClient);
    }
}
