package dev.bugi.sensor.alert.service;

import dev.bugi.sensor.alert.dto.AlertResponse;
import dev.bugi.sensor.alert.dto.DailyAlertCountResponse;
import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import dev.bugi.sensor.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock UserRepository userRepository;
    @Mock AccessControlService accessControlService;

    private static final Instant FIXED = Instant.parse("2026-07-16T00:00:00Z");
    @Spy Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

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
        Zone zone = Zone.builder()
                .factory(Factory.builder().name("테스트공장").build())
                .name("1구역")
                .build();
        return Device.builder()
                .zone(zone)
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
                .severity(AlertSeverity.CRITICAL)
                .build();
    }

    @Test
    void getAllAlerts_returns_accessible_alerts() {
        User user = mockUser();
        Device device = mockDevice();
        Alert alert = mockAlert(device);
        Pageable pageable = PageRequest.of(0, 50);

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of(1L));
        when(alertRepository.findByDeviceIdIn(eq(List.of(1L)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(alert)));

        Page<AlertResponse> result = alertService.getAllAlerts("EMP001", pageable);

        assertThat(result.getContent()).hasSize(1);
        AlertResponse mapped = result.getContent().get(0);
        assertThat(mapped.getSensorValue()).isEqualTo(95.0);
        assertThat(mapped.getThresholdValue()).isEqualTo(80.0);
        assertThat(mapped.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(mapped.getMessage()).isEqualTo("[온도센서1] 임계값 초과! 현재값: 95.0, 임계값: 80.0");
    }

    @Test
    void getAllAlerts_returns_empty_when_no_accessible_devices() {
        User user = mockUser();
        Pageable pageable = PageRequest.of(0, 50);

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of());

        Page<AlertResponse> result = alertService.getAllAlerts("EMP001", pageable);

        assertThat(result).isEmpty();
        verify(alertRepository, never()).findByDeviceIdIn(any(), any());
    }

    @Test
    void getAllAlerts_fail_user_not_found() {
        when(userRepository.findByEmployeeId("NOTEXIST")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> alertService.getAllAlerts("NOTEXIST", PageRequest.of(0, 50)));
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
        when(alertRepository.findByDeviceIdOrderByCreatedAtDesc(eq(device.getId()), any(Pageable.class)))
                .thenReturn(List.of(alert));

        List<AlertResponse> result = alertService.getAllAlertsByDeviceId("EMP001", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSensorValue()).isEqualTo(95.0);
        assertThat(result.get(0).getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(result.get(0).getMessage())
                .isEqualTo("[온도센서1] 임계값 초과! 현재값: 95.0, 임계값: 80.0");
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
        doThrow(new AccessDeniedException("접근 권한이 없어요"))
                .when(accessControlService).assertCanAccessDevice(user, device);

        assertThrows(AccessDeniedException.class,
                () -> alertService.getAllAlertsByDeviceId("EMP001", 1L));
    }

    @Test
    void getDailyCount_maps_rows_and_uses_clock_for_window() {
        User user = mockUser();
        Device device = mockDevice();

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        doNothing().when(accessControlService).assertCanAccessDevice(user, device);
        // 조회 시작 시각은 clock.instant() - 7일 이어야 한다.
        Instant expectedStart = FIXED.minus(java.time.Duration.ofDays(7));
        when(alertRepository.findDailyCountByDeviceId(eq(1L), eq(expectedStart)))
                .thenReturn(List.of(
                        new Object[]{"2026-07-15", 3L},
                        new Object[]{"2026-07-16", 5L}));

        List<DailyAlertCountResponse> result = alertService.getDailyCount("EMP001", 1L, 7);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDate()).isEqualTo("2026-07-15");
        assertThat(result.get(0).getCount()).isEqualTo(3L);
        assertThat(result.get(1).getDate()).isEqualTo("2026-07-16");
        assertThat(result.get(1).getCount()).isEqualTo(5L);
    }

    @Test
    void getDailyCount_fail_access_denied() {
        User user = mockUser();
        Device device = mockDevice();

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        doThrow(new AccessDeniedException("접근 권한이 없어요"))
                .when(accessControlService).assertCanAccessDevice(user, device);

        assertThrows(AccessDeniedException.class,
                () -> alertService.getDailyCount("EMP001", 1L, 7));
        verify(alertRepository, never()).findDailyCountByDeviceId(any(), any());
    }

    @Test
    void getAllAlerts_returns_empty_when_no_alerts_for_accessible_devices() {
        User user = mockUser();
        Pageable pageable = PageRequest.of(0, 50);

        when(userRepository.findByEmployeeId("EMP001")).thenReturn(Optional.of(user));
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of(1L));
        when(alertRepository.findByDeviceIdIn(eq(List.of(1L)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<AlertResponse> result = alertService.getAllAlerts("EMP001", pageable);

        assertThat(result).isEmpty();
    }
}
