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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FreshnessSchedulerTest {

    @Mock DeviceRepository deviceRepository;
    @Mock FailedReadingRepository failedReadingRepository;
    @Mock AlertRepository alertRepository;
    @Mock AxClient axClient;
    @Mock AxProperties axProperties;

    @InjectMocks FreshnessScheduler scheduler;

    private Device silentDevice() {
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(1L);
        when(device.getName()).thenReturn("엔진1-온도(s4)");
        when(device.getExpectedIntervalSeconds()).thenReturn(10);
        when(device.getLastSeenAt()).thenReturn(LocalDateTime.now().minusSeconds(120));
        return device;
    }

    @Test
    void 침묵장치_AX활성_원인진단이_담긴_CRITICAL_알림생성() {
        Device device = silentDevice();
        when(deviceRepository.findByExpectedIntervalSecondsIsNotNull()).thenReturn(List.of(device));
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
        assertThat(alert.getSensorValue()).isNull();
    }

    @Test
    void 같은_침묵episode는_틱마다_재알림하지_않는다() {
        Device device = silentDevice();
        when(deviceRepository.findByExpectedIntervalSecondsIsNotNull()).thenReturn(List.of(device));
        when(axProperties.isEnabled()).thenReturn(true);
        when(axClient.diagnoseFreshness(any()))
                .thenReturn(new FreshnessDiagnoseResponse("소스 침묵 의심", "리포트", "echo"));

        scheduler.checkFreshness();
        scheduler.checkFreshness();

        verify(alertRepository, times(1)).save(any());
    }

    @Test
    void AX비활성이면_진단없이_알림만_생성() {
        Device device = silentDevice();
        when(deviceRepository.findByExpectedIntervalSecondsIsNotNull()).thenReturn(List.of(device));
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
        Device fresh = mock(Device.class);
        when(fresh.getExpectedIntervalSeconds()).thenReturn(60);
        when(fresh.getLastSeenAt()).thenReturn(LocalDateTime.now().minusSeconds(5));
        when(deviceRepository.findByExpectedIntervalSecondsIsNotNull()).thenReturn(List.of(fresh));

        scheduler.checkFreshness();

        verifyNoInteractions(alertRepository, axClient);
    }

    @Test
    void 한번도_수신없는_장치는_건너뛴다() {
        Device neverSeen = mock(Device.class);
        when(neverSeen.getLastSeenAt()).thenReturn(null);
        when(deviceRepository.findByExpectedIntervalSecondsIsNotNull()).thenReturn(List.of(neverSeen));

        scheduler.checkFreshness();

        verifyNoInteractions(alertRepository, axClient);
    }
}
