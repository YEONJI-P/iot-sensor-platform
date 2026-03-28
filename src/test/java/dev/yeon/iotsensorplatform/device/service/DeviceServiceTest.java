package dev.yeon.iotsensorplatform.device.service;

import dev.yeon.iotsensorplatform.device.dto.DeviceRegisterRequest;
import dev.yeon.iotsensorplatform.device.dto.DeviceUpdateRequest;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
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

// User 정보 유효성은 JWTUtil에서 하므로 이 테스트에서는 User정보 별도 유효검증 X
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    DeviceRepository deviceRepository;
    @Mock
    UserRepository userRepository;

    @InjectMocks
    DeviceService deviceService;

    private User mockUser() {
        return User.builder()
                .employeeId("EMP001")
                .name("홍길동")
                .password("encoded_password")
                .role(Role.DEVICE_MANAGER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void register() {
        User user = mockUser();
        DeviceRegisterRequest request = new DeviceRegisterRequest(
                "온도센서1", Device.DeviceType.TEMPERATURE, "공장1층", 80.0);

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));

        deviceService.register(request, "EMP001");

        verify(deviceRepository, times(1)).save(any(Device.class));
    }

    @Test
    void update_success() {
        User user = mockUser();
        Device mockDevice = Device.builder()
                .user(user)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "온도센서1_update", Device.DeviceType.TEMPERATURE, "공장2층", 75.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(mockDevice));

        deviceService.update(1L, request, "EMP001");

        verify(deviceRepository, times(1)).save(any(Device.class));
    }

    @Test
    void update_fail_device_id() {
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "온도센서1_update", Device.DeviceType.TEMPERATURE, "공장2층", 75.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> deviceService.update(1L, request, "EMP001"));
    }

    @Test
    void update_fail_not_owner() {
        User user = mockUser();
        Device mockDevice = Device.builder()
                .user(user)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "온도센서1_update", Device.DeviceType.TEMPERATURE, "공장2층", 75.0);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(mockDevice));

        assertThrows(IllegalArgumentException.class,
                () -> deviceService.update(1L, request, "OTHER001"));
    }

    @Test
    void delete_success() {
        User user = mockUser();
        Device mockDevice = Device.builder()
                .user(user)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(mockDevice));

        deviceService.delete(1L, "EMP001");

        verify(deviceRepository, times(1)).delete(mockDevice);
    }

    @Test
    void delete_fail_device_id() {
        when(deviceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> deviceService.delete(1L, "EMP001"));
    }

    @Test
    void delete_fail_not_owner() {
        User user = mockUser();
        Device mockDevice = Device.builder()
                .user(user)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(mockDevice));

        assertThrows(IllegalArgumentException.class,
                () -> deviceService.delete(1L, "OTHER001"));
    }
}
