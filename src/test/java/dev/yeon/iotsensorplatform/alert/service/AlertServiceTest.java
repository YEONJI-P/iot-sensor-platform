package dev.yeon.iotsensorplatform.alert.service;

import dev.yeon.iotsensorplatform.alert.dto.AlertResponse;
import dev.yeon.iotsensorplatform.alert.entity.Alert;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.organization.entity.OrgGroup;
import dev.yeon.iotsensorplatform.organization.entity.Organization;
import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock UserRepository userRepository;
    @Mock AccessControlService accessControlService;

    @InjectMocks
    AlertService alertService;

    private User mockUser() {
        return User.builder()
                .employeeId("EMP001")
                .name("홍길동")
                .password("encoded_password")
                .role(Role.VIEWER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Device mockDevice() {
        OrgGroup group = OrgGroup.builder()
                .organization(Organization.builder().name("테스트조직").build())
                .name("1구역")
                .build();
        return Device.builder()
                .group(group)
                .name("온도센서1")
                .type(Device.DeviceType.TEMPERATURE)
                .location("공장1층")
                .thresholdValue(80.0)
                .build();
    }

    private Alert mockAlert(Device device) {
        return Alert.builder()
                .device(device)
                .sensorValue(95.0)
                .thresholdValue(80.0)
                .message("[온도센서1] 임계값 초과! 현재값: 95.0, 임계값: 80.0")
                .build();
    }

    @Test
    void getAllAlerts_returns_accessible_alerts() {
        User user = mockUser();
        Device device = mockDevice();
        Alert alert = mockAlert(device);

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of(1L));
        when(alertRepository.findAllByDeviceIdInOrderByCreatedAtDesc(List.of(1L))).thenReturn(List.of(alert));

        List<AlertResponse> result = alertService.getAllAlerts("EMP001");

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllAlerts_returns_empty_when_no_accessible_devices() {
        User user = mockUser();

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of());

        List<AlertResponse> result = alertService.getAllAlerts("EMP001");

        assertThat(result).isEmpty();
        verify(alertRepository, never()).findAllByDeviceIdInOrderByCreatedAtDesc(any());
    }

    @Test
    void getAllAlerts_fail_user_not_found() {
        when(userRepository.findByEmployeeId("NOTEXIST")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> alertService.getAllAlerts("NOTEXIST"));
    }

    @Test
    void getAllAlertsByDeviceId_fail_device_not_found() {
        User user = mockUser();

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> alertService.getAllAlertsByDeviceId("EMP001", 99L));
    }

    @Test
    void getAllAlertsByDeviceId_success() {
        User user = mockUser();
        Device device = mockDevice();
        Alert alert = mockAlert(device);

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        doNothing().when(accessControlService).assertCanAccessDevice(user, device);
        when(alertRepository.findAllByDeviceIdOrderByCreatedAtDesc(device.getId())).thenReturn(List.of(alert));

        List<AlertResponse> result = alertService.getAllAlertsByDeviceId("EMP001", 1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getAllAlertsByDeviceId_fail_user_not_found(){
        when(userRepository.findByEmployeeId("NOTEXIST")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,()->alertService.getAllAlertsByDeviceId("NOTEXIST",1L));
    }

    @Test
    void getAllAlertsByDeviceId_fail_access_denied() {
        User user = mockUser();
        Device device = mockDevice();

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        doThrow(new IllegalArgumentException("접근 권한이 없어요"))
                .when(accessControlService).assertCanAccessDevice(user, device);

        assertThrows(IllegalArgumentException.class,
                () -> alertService.getAllAlertsByDeviceId("EMP001", 1L));
    }

    @Test
    void getAllAlerts_returns_empty_when_no_alerts_for_accessible_devices() {
        User user = mockUser();

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of(1L));
        when(alertRepository.findAllByDeviceIdInOrderByCreatedAtDesc(List.of(1L)))
                .thenReturn(List.of());

        List<AlertResponse> result = alertService.getAllAlerts("EMP001");

        assertThat(result).isEmpty();
    }
}
