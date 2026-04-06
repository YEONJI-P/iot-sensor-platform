package dev.yeon.iotsensorplatform.sensordata.service;

import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.entity.SensorData;
import dev.yeon.iotsensorplatform.sensordata.kafka.SensorDataProducer;
import dev.yeon.iotsensorplatform.sensordata.repository.SensorDataRepository;
import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
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
    @Mock SensorDataRepository sensorDataRepository;
    @Mock AlertRepository alertRepository;
    @Mock UserRepository userRepository;
    @Mock AccessControlService accessControlService;
    @Mock Optional<SensorDataProducer> sensorDataProducer;

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

    private User mockUser() {
        return User.builder()
                .employeeId("EMP001")
                .name("홍길동")
                .password("encoded_password")
                .role(Role.DATA_INPUTTER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    // Kafka 없는 환경(prod 외): sensorDataProducer = empty → 직접 저장
    @Test
    void receive_auto_saves_sensordata() {
        Device device = mockDevice(80.0);
        SensorDataRequest request = new SensorDataRequest(1L, 50.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(sensorDataProducer.isPresent()).thenReturn(false);

        sensorDataService.receive(request);

        verify(sensorDataRepository, times(1)).save(any(SensorData.class));
        verify(alertRepository, never()).save(any());
    }

    @Test
    void receive_auto_creates_alert_when_threshold_exceeded() {
        Device device = mockDevice(80.0);
        SensorDataRequest request = new SensorDataRequest(1L, 95.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(sensorDataProducer.isPresent()).thenReturn(false);

        sensorDataService.receive(request);

        verify(sensorDataRepository, times(1)).save(any(SensorData.class));
        verify(alertRepository, times(1)).save(any());
    }

    @Test
    void receive_auto_no_alert_when_no_threshold() {
        Device device = mockDevice(null);
        SensorDataRequest request = new SensorDataRequest(1L, 999.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(sensorDataProducer.isPresent()).thenReturn(false);

        sensorDataService.receive(request);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void receive_fail_device_not_found() {
        SensorDataRequest request = new SensorDataRequest(99L, 50.0);
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> sensorDataService.receive(request));
    }

    @Test
    void receive_manual_saves_with_user() {
        Device device = mockDevice(80.0);
        User user = mockUser();
        SensorDataRequest request = new SensorDataRequest(1L, 50.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(sensorDataProducer.isPresent()).thenReturn(false);

        sensorDataService.receive(request, "EMP001");

        verify(sensorDataRepository, times(1)).save(any(SensorData.class));
    }
}
