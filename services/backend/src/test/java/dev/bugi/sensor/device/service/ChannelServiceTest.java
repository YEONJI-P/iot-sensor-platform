package dev.bugi.sensor.device.service;

import dev.bugi.sensor.device.dto.ChannelCreateRequest;
import dev.bugi.sensor.device.dto.ChannelResponse;
import dev.bugi.sensor.device.dto.ChannelUpdateRequest;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.device.repository.SensorChannelRepository;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock SensorChannelRepository sensorChannelRepository;
    @Mock AccessControlService accessControlService;

    @InjectMocks
    ChannelService channelService;

    private User mockUser() {
        return mockUser(Role.MEMBER);
    }

    private User mockUser(Role role) {
        return User.builder().employeeId("EMP001").name("담당자").password("pw")
                .role(role).status(UserStatus.ACTIVE).build();
    }

    private SensorChannel channel(Device device) {
        return SensorChannel.builder().device(device).code("s4").unit("°R")
                .quantityKind("temperature").thresholdValue(1416.0)
                .thresholdDirection(ThresholdDirection.ABOVE).build();
    }

    private Device device() {
        Zone zone = Zone.builder().factory(Factory.builder().name("엔진시험동").build()).name("엔진1구역").build();
        return Device.builder().zone(zone).code("CMAPSS-U1").name("엔진 유닛1").location("L").build();
    }

    @Test
    void getMyChannels_without_deviceId_returns_accessible_scope() {
        User user = mockUser();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of(1L));
        when(sensorChannelRepository.findByDeviceIdInWithDeviceAndZone(List.of(1L)))
                .thenReturn(List.of(channel(device())));

        List<ChannelResponse> result = channelService.getMyChannels("EMP001", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("s4");
        verify(accessControlService, never()).assertCanAccessDevice(any(), any());
    }

    @Test
    void getMyChannels_without_deviceId_empty_when_no_access() {
        User user = mockUser();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of());

        assertThat(channelService.getMyChannels("EMP001", null)).isEmpty();
        verify(sensorChannelRepository, never()).findByDeviceIdInWithDeviceAndZone(any());
    }

    @Test
    void getMyChannels_with_deviceId_filters_to_that_device_after_access_check() {
        User user = mockUser();
        Device device = device();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        doNothing().when(accessControlService).assertCanAccessDevice(user, device);
        when(sensorChannelRepository.findByDeviceIdInWithDeviceAndZone(List.of(1L)))
                .thenReturn(List.of(channel(device)));

        List<ChannelResponse> result = channelService.getMyChannels("EMP001", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deviceCode()).isEqualTo("CMAPSS-U1");
        // 접근 범위 전체 조회 경로는 타지 않는다.
        verify(accessControlService, never()).getAccessibleDeviceIds(any());
    }

    @Test
    void getMyChannels_with_deviceId_access_denied() {
        User user = mockUser();
        Device device = device();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        doThrow(new AccessDeniedException("접근 권한이 없는 장치예요"))
                .when(accessControlService).assertCanAccessDevice(user, device);

        assertThrows(AccessDeniedException.class, () -> channelService.getMyChannels("EMP001", 1L));
    }

    @Test
    void factory_admin_getMyChannels_same_factory_allowed() {
        User user = mockUser(Role.FACTORY_ADMIN);
        Device device = device();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(sensorChannelRepository.findByDeviceIdInWithDeviceAndZone(List.of(1L)))
                .thenReturn(List.of(channel(device)));

        assertThat(channelService.getMyChannels("EMP001", 1L)).hasSize(1);
        verify(accessControlService).assertCanAccessDevice(user, device);
    }

    @Test
    void factory_admin_getMyChannels_other_factory_forbidden() {
        User user = mockUser(Role.FACTORY_ADMIN);
        Device device = device();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(deviceRepository.findById(2L)).thenReturn(Optional.of(device));
        doThrow(new AccessDeniedException("접근 권한이 없는 장치예요"))
                .when(accessControlService).assertCanAccessDevice(user, device);

        assertThrows(AccessDeniedException.class, () -> channelService.getMyChannels("EMP001", 2L));
        verifyNoInteractions(sensorChannelRepository);
    }

    @Test
    void getMyChannels_with_deviceId_device_not_found() {
        User user = mockUser();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> channelService.getMyChannels("EMP001", 99L));
    }

    @Test
    void createChannel_success() {
        User user = mockUser();
        Device device = device();
        ChannelCreateRequest request = new ChannelCreateRequest("s11", "psia", "pressure", 47.8, ThresholdDirection.ABOVE);

        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        channelService.createChannel(1L, request, "EMP001");

        verify(accessControlService).assertCanMutateDevice(user);
        verify(accessControlService).assertCanAccessDevice(user, device);
        verify(sensorChannelRepository).save(any(SensorChannel.class));
    }

    @Test
    void updateChannel_success_updates_threshold() {
        User user = mockUser();
        SensorChannel channel = channel(device());
        ChannelUpdateRequest request = new ChannelUpdateRequest("°R", "temperature", 1500.0, ThresholdDirection.ABOVE);

        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getChannel(5L)).thenReturn(channel);

        channelService.updateChannel(5L, request, "EMP001");

        verify(accessControlService).assertCanAccessChannel(user, channel);
        verify(sensorChannelRepository).save(channel);
        assertThat(channel.getThresholdValue()).isEqualTo(1500.0);
    }

    @Test
    void createChannel_rejects_non_positive_absolute_threshold() {
        User user = mockUser();
        Device device = device();
        ChannelCreateRequest request = new ChannelCreateRequest(
                "X1_CurrentFeedback", "A", "current", 0.0, ThresholdDirection.ABS_ABOVE);
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> channelService.createChannel(1L, request, "EMP001"));

        assertThat(error.getMessage()).contains("0보다 커야");
        verify(sensorChannelRepository, never()).save(any());
    }

    @Test
    void updateChannel_rejects_negative_absolute_threshold() {
        User user = mockUser();
        SensorChannel channel = channel(device());
        ChannelUpdateRequest request = new ChannelUpdateRequest(
                "A", "current", -1.0, ThresholdDirection.ABS_ABOVE);
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getChannel(5L)).thenReturn(channel);

        assertThrows(IllegalArgumentException.class,
                () -> channelService.updateChannel(5L, request, "EMP001"));

        verify(sensorChannelRepository, never()).save(any());
    }
}
