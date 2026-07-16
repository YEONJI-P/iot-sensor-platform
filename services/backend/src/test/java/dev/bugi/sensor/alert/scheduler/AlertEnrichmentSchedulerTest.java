package dev.bugi.sensor.alert.scheduler;

import dev.bugi.sensor.alert.dto.EnrichTarget;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.ax.client.AxClient;
import dev.bugi.sensor.ax.config.AxProperties;
import dev.bugi.sensor.ax.dto.AnomalyExplainRequest;
import dev.bugi.sensor.ax.dto.AnomalyExplainResponse;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.sensordata.entity.SensorData;
import dev.bugi.sensor.sensordata.repository.SensorDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertEnrichmentSchedulerTest {

    @Mock AlertRepository alertRepository;
    @Mock SensorDataRepository sensorDataRepository;
    @Mock AxClient axClient;
    @Mock AxProperties axProperties;

    @Captor ArgumentCaptor<AnomalyExplainRequest> requestCaptor;

    @InjectMocks
    AlertEnrichmentScheduler scheduler;

    private EnrichTarget target(Double threshold) {
        return new EnrichTarget(
                1L, 7L, "엔진1-온도", Device.DeviceType.TEMPERATURE, 1500.0, threshold, "임계 초과");
    }

    private SensorData reading(double value) {
        return SensorData.builder()
                .device(Device.builder().name("엔진1-온도").type(Device.DeviceType.TEMPERATURE).build())
                .value(value)
                .build();
    }

    @Test
    void AX_요청에_센서_단위가_포함된다() {
        when(axProperties.isEnabled()).thenReturn(true);
        when(alertRepository.findEnrichTargets(any())).thenReturn(List.of(target(1416.0)));
        when(sensorDataRepository.findByDeviceIdOrderByRecordedAtDesc(eq(7L), any()))
                .thenReturn(List.of());
        when(axClient.explainAnomaly(any()))
                .thenReturn(new AnomalyExplainResponse("근거", "권고", "WARNING", "echo"));

        scheduler.enrichAlerts();

        verify(axClient).explainAnomaly(requestCaptor.capture());
        assertThat(requestCaptor.getValue().unit())
                .isEqualTo(Device.DeviceType.TEMPERATURE.getUnit());
    }

    @Test
    void 윈도우_지표_초과율_추세_변동성이_요청에_실린다() {
        when(axProperties.isEnabled()).thenReturn(true);
        when(alertRepository.findEnrichTargets(any())).thenReturn(List.of(target(100.0)));
        // 임계 100 기준: 10건 중 4건 초과 → 초과율 0.4, 뒤로 갈수록 상승 추세.
        when(sensorDataRepository.findByDeviceIdOrderByRecordedAtDesc(eq(7L), any()))
                .thenReturn(List.of( // 최신순(desc)으로 반환된다고 가정
                        reading(130), reading(120), reading(110), reading(105),
                        reading(95), reading(90), reading(85), reading(80),
                        reading(75), reading(70)));
        when(axClient.explainAnomaly(any()))
                .thenReturn(new AnomalyExplainResponse("근거", "권고", "WARNING", "echo"));

        scheduler.enrichAlerts();

        verify(axClient).explainAnomaly(requestCaptor.capture());
        AnomalyExplainRequest req = requestCaptor.getValue();
        assertThat(req.breachRate()).isEqualTo(0.4, within(1e-9));
        assertThat(req.trend()).isGreaterThan(0.0);      // 상승 추세
        assertThat(req.volatility()).isGreaterThan(0.0);
        assertThat(req.recentValues()).hasSize(10);
    }

    @Test
    void 표본_부족이면_지표는_null이고_보강은_계속된다() {
        when(axProperties.isEnabled()).thenReturn(true);
        when(alertRepository.findEnrichTargets(any())).thenReturn(List.of(target(100.0)));
        when(sensorDataRepository.findByDeviceIdOrderByRecordedAtDesc(eq(7L), any()))
                .thenReturn(List.of(reading(130), reading(120))); // MIN_SAMPLES 미만
        when(axClient.explainAnomaly(any()))
                .thenReturn(new AnomalyExplainResponse("근거", "권고", "WARNING", "echo"));

        scheduler.enrichAlerts();

        verify(axClient).explainAnomaly(requestCaptor.capture());
        AnomalyExplainRequest req = requestCaptor.getValue();
        assertThat(req.breachRate()).isNull();
        assertThat(req.trend()).isNull();
        assertThat(req.volatility()).isNull();
    }
}
