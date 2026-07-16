package dev.bugi.sensor.sensordata.service;

import dev.bugi.sensor.alert.entity.Alert;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorDataServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock SensorDataRepository sensorDataRepository;
    @Mock AlertRepository alertRepository;
    @Mock FailedReadingRepository failedReadingRepository;
    @Mock AnomalyDetector anomalyDetector;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock AccessControlService accessControlService; // GET Test
    @Mock UserRepository userRepository; // GET Test
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    SensorDataService sensorDataService;

    private Device mockDevice(Double threshold) {
        return Device.builder()
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(threshold)
                .build();
    }

    @Test
    void receive_saves_sensordata() {
        Device device = mockDevice(80.0);
        SensorDataRequest request = new SensorDataRequest(1L, 50.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        sensorDataService.receive(request);

        verify(sensorDataRepository, times(1)).save(any(SensorData.class));
    }

    @Test
    void receive_normal_value_no_alert() {
        Device device = mockDevice(80.0);
        SensorDataRequest request = new SensorDataRequest(1L, 50.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(anomalyDetector.isAnomaly(any(Device.class), anyDouble())).thenReturn(false);

        sensorDataService.receive(request);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void receive_anomaly_creates_alert() {
        Device device = mockDevice(80.0);
        SensorDataRequest request = new SensorDataRequest(1L, 100.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(anomalyDetector.isAnomaly(any(Device.class), anyDouble())).thenReturn(true);

        sensorDataService.receive(request);

        verify(alertRepository, times(1)).save(any(Alert.class));
    }

    @Test
    void receive_device_not_found_records_failure() {
        SensorDataRequest request = new SensorDataRequest(99L, 50.0);
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        sensorDataService.receive(request);

        verify(failedReadingRepository, times(1)).save(any(FailedReading.class));
        verify(sensorDataRepository, never()).save(any());
    }

    @Test
    void receive_invalid_request_records_failure() {
        SensorDataRequest request = new SensorDataRequest(null, 50.0);

        sensorDataService.receive(request);

        verify(failedReadingRepository, times(1)).save(any(FailedReading.class));
        verify(deviceRepository, never()).findById(any());
        verify(sensorDataRepository, never()).save(any());
    }
}
