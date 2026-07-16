package dev.bugi.sensor.device.service;

import dev.bugi.sensor.device.dto.DeviceRegisterRequest;
import dev.bugi.sensor.device.dto.DeviceUpdateRequest;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.repository.ZoneRepository;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import dev.bugi.sensor.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock UserRepository userRepository;
    @Mock ZoneRepository zoneRepository;
    @Mock AccessControlService accessControlService;

    @InjectMocks
    DeviceService deviceService;

    private User mockUser() {
        return User.builder()
                .employeeId("DEV001")
                .name("장치담당자")
                .password("encoded_password")
                .role(Role.MEMBER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private User viewerUser() {
        return User.builder()
                .employeeId("VWR001")
                .name("열람자")
                .password("encoded_password")
                .role(Role.VIEWER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Zone mockZone() {
        Factory org = Factory.builder().name("테스트공장").build();
        return Zone.builder().factory(org).name("1구역").build();
    }

    private Device mockDevice(Zone zone) {
        return Device.builder()
                .zone(zone)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();
    }

    @Test
    void register_success() {
        User user = mockUser();
        Zone zone = mockZone();
        DeviceRegisterRequest request = new DeviceRegisterRequest(
                "온도센서1", Device.DeviceType.TEMPERATURE, "공장1층", 80.0, 1L);

        when(userRepository.findByEmployeeId("DEV001")).thenReturn(Optional.of(user));
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zone));
        doNothing().when(accessControlService).assertCanManageZone(user, zone);

        deviceService.register(request, "DEV001");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("온도센서1");
        assertThat(saved.getType()).isEqualTo(Device.DeviceType.TEMPERATURE);
        assertThat(saved.getLocation()).isEqualTo("공장1층");
        assertThat(saved.getThresholdValue()).isEqualTo(80.0);
        assertThat(saved.getZone()).isSameAs(zone);
    }

    @Test
    void register_fail_zone_not_found() {
        User user = mockUser();
        DeviceRegisterRequest request = new DeviceRegisterRequest(
                "온도센서1", Device.DeviceType.TEMPERATURE, "공장1층", 80.0, 99L);

        when(userRepository.findByEmployeeId("DEV001")).thenReturn(Optional.of(user));
        when(zoneRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> deviceService.register(request, "DEV001"));
    }

    @Test
    void update_success() {
        User user = mockUser();
        Zone zone = mockZone();
        Device device = mockDevice(zone);
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "온도센서1_update", Device.DeviceType.TEMPERATURE, "공장2층", 75.0);

        when(userRepository.findByEmployeeId("DEV001")).thenReturn(Optional.of(user));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        doNothing().when(accessControlService).assertCanAccessDevice(user, device);

        deviceService.update(1L, request, "DEV001");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("온도센서1_update");
        assertThat(saved.getType()).isEqualTo(Device.DeviceType.TEMPERATURE);
        assertThat(saved.getLocation()).isEqualTo("공장2층");
        assertThat(saved.getThresholdValue()).isEqualTo(75.0);
    }

    @Test
    void update_fail_device_not_found() {
        User user = mockUser();
        when(userRepository.findByEmployeeId("DEV001")).thenReturn(Optional.of(user));
        when(deviceRepository.findById(1L)).thenReturn(Optional.empty());
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "온도센서1_update", Device.DeviceType.TEMPERATURE, "공장2층", 75.0);

        assertThrows(IllegalArgumentException.class,
                () -> deviceService.update(1L, request, "DEV001"));
    }

    @Test
    void delete_success() {
        User user = mockUser();
        Zone zone = mockZone();
        Device device = mockDevice(zone);

        when(userRepository.findByEmployeeId("DEV001")).thenReturn(Optional.of(user));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        doNothing().when(accessControlService).assertCanAccessDevice(user, device);

        deviceService.delete(1L, "DEV001");

        verify(deviceRepository, times(1)).delete(device);
    }

    @Test
    void delete_fail_device_not_found() {
        User user = mockUser();
        when(userRepository.findByEmployeeId("DEV001")).thenReturn(Optional.of(user));
        when(deviceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> deviceService.delete(1L, "DEV001"));
    }

    @Test
    void register_viewer_forbidden() {
        User viewer = viewerUser();
        DeviceRegisterRequest request = new DeviceRegisterRequest(
                "온도센서1", Device.DeviceType.TEMPERATURE, "공장1층", 80.0, 1L);

        when(userRepository.findByEmployeeId("VWR001")).thenReturn(Optional.of(viewer));
        doThrow(new AccessDeniedException("열람 전용 계정은 장치를 변경할 수 없어요"))
                .when(accessControlService).assertCanMutateDevice(viewer);

        assertThrows(AccessDeniedException.class,
                () -> deviceService.register(request, "VWR001"));
        verify(deviceRepository, never()).save(any(Device.class));
    }
}
