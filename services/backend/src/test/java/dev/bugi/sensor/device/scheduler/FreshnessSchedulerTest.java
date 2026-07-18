package dev.bugi.sensor.device.scheduler;

import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.explain.client.ExplainClient;
import dev.bugi.sensor.explain.config.ExplainProperties;
import dev.bugi.sensor.explain.dto.FreshnessDiagnoseRequest;
import dev.bugi.sensor.explain.dto.FreshnessDiagnoseResponse;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FreshnessSchedulerTest {

    @Mock DeviceStatusRepository deviceStatusRepository;
    @Mock FailedReadingRepository failedReadingRepository;
    @Mock AlertRepository alertRepository;
    @Mock ExplainClient explainClient;
    @Mock ExplainProperties explainProperties;
    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    @Spy Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks FreshnessScheduler scheduler;

    private DeviceStatus device(long id, long zoneId, long silentSeconds) {
        Zone zone = mock(Zone.class);
        when(zone.getId()).thenReturn(zoneId);
        // 이름·id 는 알림(코호트/개별)을 만드는 경로에서만 읽히므로 정상 시나리오에선 미사용.
        lenient().when(zone.getName()).thenReturn("Z" + zoneId);
        Device device = mock(Device.class);
        lenient().when(device.getId()).thenReturn(id);
        lenient().when(device.getName()).thenReturn("dev" + id);
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
    void 혼자_침묵하면_explain진단이_담긴_CRITICAL() {
        DeviceStatus d1 = silent(1, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1));
        when(explainProperties.isEnabled()).thenReturn(true);
        when(failedReadingRepository.countByDeviceIdAndCreatedAtAfter(eq(1L), any())).thenReturn(0);
        when(explainClient.diagnoseFreshness(any(FreshnessDiagnoseRequest.class)))
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
        when(explainProperties.isEnabled()).thenReturn(true);
        when(explainClient.diagnoseFreshness(any())).thenReturn(new FreshnessDiagnoseResponse("c", "r", "echo"));

        scheduler.checkFreshness();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        verify(explainClient).diagnoseFreshness(any());
    }

    @Test
    void 구역_전체가_동시_침묵하면_WARNING_집계_1건_explain호출없음() {
        // 두 장치 모두 침묵 = 계획정지/게이트웨이 가능성 → 장치별 CRITICAL 대신 구역 1건.
        DeviceStatus d1 = silent(1, 100), d2 = silent(2, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1, d2));

        scheduler.checkFreshness();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(captor.getValue().getMessage()).contains("구역 전체");
        verify(explainClient, never()).diagnoseFreshness(any());
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
        when(explainProperties.isEnabled()).thenReturn(true);
        when(explainClient.diagnoseFreshness(any())).thenReturn(new FreshnessDiagnoseResponse("c", "r", "echo"));

        scheduler.checkFreshness();
        scheduler.checkFreshness();

        verify(alertRepository, times(1)).save(any());
    }

    @Test
    void explain비활성이면_진단없이_알림만() {
        DeviceStatus d1 = silent(1, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1));
        when(explainProperties.isEnabled()).thenReturn(false);

        scheduler.checkFreshness();

        verify(explainClient, never()).diagnoseFreshness(any());
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

        verifyNoInteractions(alertRepository, explainClient);
    }

    @Test
    void 경과가_기대주기와_정확히_같으면_정상() {
        // elapsed > expected 만 침묵. elapsed == expected(10s) 는 경계 안쪽 → 알림 없음.
        DeviceStatus d1 = device(1, 100, 10);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1));

        scheduler.checkFreshness();

        verifyNoInteractions(alertRepository, explainClient);
    }

    @Test
    void 일부만_침묵하면_침묵한_장치들만_개별_CRITICAL() {
        // 3대 중 2대 침묵(silent < seen) → 구역 집계가 아니라 침묵한 2대 각각 개별 처리.
        DeviceStatus d1 = silent(1, 100), d2 = silent(2, 100), d3 = healthy(3, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1, d2, d3));
        when(explainProperties.isEnabled()).thenReturn(true);
        when(explainClient.diagnoseFreshness(any())).thenReturn(new FreshnessDiagnoseResponse("c", "r", "echo"));

        scheduler.checkFreshness();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allMatch(a -> a.getSeverity() == AlertSeverity.CRITICAL);
        verify(explainClient, times(2)).diagnoseFreshness(any());
    }

    @Test
    void explain호출이_실패해도_진단없이_알림은_저장된다() {
        // diagnose()의 try-catch(의도적 방어) 경로: 예외가 나도 evidence=null 로 CRITICAL 저장.
        DeviceStatus d1 = silent(1, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1));
        when(explainProperties.isEnabled()).thenReturn(true);
        when(explainClient.diagnoseFreshness(any())).thenThrow(new RuntimeException("explain 다운"));

        scheduler.checkFreshness();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert alert = captor.getValue();
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alert.getEvidence()).isNull();
        assertThat(alert.getRecommendation()).isNull();
    }

    // ── DeviceStatus 축소(inAlarm 제거) 이후 경계 확인 ───────────────────
    // freshness Alert 는 device 만 알고 어떤 channel·batch 에서 났는지 모른다(수신 자체가 끊긴 사건).
    // Alert.channel/batch 를 device_status 축소와 무관하게 항상 null 로 남기는지 회귀 고정한다.

    @Test
    void 개별_침묵_Alert는_channel과_batch가_null이다() {
        DeviceStatus d1 = silent(1, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1));
        when(explainProperties.isEnabled()).thenReturn(false);

        scheduler.checkFreshness();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isNull();
        assertThat(captor.getValue().getBatch()).isNull();
        assertThat(captor.getValue().getDevice()).isNotNull();
    }

    @Test
    void 구역_전체_침묵_Alert도_channel과_batch가_null이다() {
        DeviceStatus d1 = silent(1, 100), d2 = silent(2, 100);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(d1, d2));

        scheduler.checkFreshness();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isNull();
        assertThat(captor.getValue().getBatch()).isNull();
        assertThat(captor.getValue().getDevice()).isNotNull();
    }

    @Test
    void 수신시각_없는_상태행은_건너뛴다() {
        // 한 번도 수신 없는 장치는 device_status 행 자체가 없어 조회(JOIN)에서 빠진다.
        // 그래도 lastSeenAt 이 비어 있는 행은 방어적으로 건너뛴다.
        DeviceStatus neverSeen = mock(DeviceStatus.class);
        when(neverSeen.getLastSeenAt()).thenReturn(null);
        when(deviceStatusRepository.findMonitoredWithDeviceAndZone()).thenReturn(List.of(neverSeen));

        scheduler.checkFreshness();

        verifyNoInteractions(alertRepository, explainClient);
    }
}
