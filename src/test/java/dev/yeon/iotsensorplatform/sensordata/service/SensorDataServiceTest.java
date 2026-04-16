package dev.yeon.iotsensorplatform.sensordata.service;

import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.kafka.SensorDataProducer;
import dev.yeon.iotsensorplatform.sensordata.repository.SensorDataRepository;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorDataServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock SensorDataProducer sensorDataProducer;
    @Mock SensorDataRepository sensorDataRepository; // GET Test
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

        verify(sensorDataProducer,times(1)).send(request);
    }

    @Test
    void receive_fail_device_not_found() {
        SensorDataRequest request = new SensorDataRequest(99L, 50.0);
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> sensorDataService.receive(request));
        verify(sensorDataProducer,never()).send(any());
    }


}
