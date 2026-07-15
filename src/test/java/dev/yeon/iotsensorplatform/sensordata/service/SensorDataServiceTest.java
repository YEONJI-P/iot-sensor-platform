package dev.yeon.iotsensorplatform.sensordata.service;

import dev.yeon.iotsensorplatform.alert.entity.Alert;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.sensordata.anomaly.AnomalyDetector;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.entity.SensorData;
import dev.yeon.iotsensorplatform.sensordata.failure.FailedReading;
import dev.yeon.iotsensorplatform.sensordata.failure.FailedReadingRepository;
import dev.yeon.iotsensorplatform.sensordata.repository.SensorDataRepository;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock AccessControlService accessControlService; // GET Test
    @Mock UserRepository userRepository; // GET Test

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
