package dev.bugi.sensor.alert.service;

import dev.bugi.sensor.alert.dto.AlertResponse;
import dev.bugi.sensor.alert.dto.DailyAlertCountResponse;
import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock AccessControlService accessControlService;

    private static final Instant FIXED = Instant.parse("2026-07-16T00:00:00Z");
    @Spy Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

    @InjectMocks
    AlertService alertService;

    private User mockUser() {
        return User.builder()
                .employeeId("EMP001").name("홍길동").password("encoded_password")
                .role(Role.VIEWER).status(UserStatus.ACTIVE).build();
    }

    private Device mockDevice() {
        Zone zone = Zone.builder().factory(Factory.builder().name("테스트공장").build()).name("1구역").build();
        return Device.builder().zone(zone).code("CMAPSS-U1").name("엔진 유닛1").location("L").build();
    }

    private SensorChannel mockChannel() {
        return SensorChannel.builder().device(mockDevice()).code("s4").unit("°R")
                .quantityKind("temperature").thresholdValue(80.0)
                .thresholdDirection(ThresholdDirection.ABOVE).build();
    }

    private Alert mockAlert(SensorChannel channel) {
        return Alert.builder()
                .device(channel.getDevice()).channel(channel)
                .sensorValue(95.0).thresholdValue(80.0)
                .message("[엔진 유닛1/s4] 임계값 초과! 현재값: 95.0, 임계값: 80.0")
                .severity(AlertSeverity.CRITICAL).build();
    }

    // ── 전체 알림(device 스코프) ────────────────────────────────────────

    @Test
    void getAllAlerts_returns_accessible_alerts() {
        User user = mockUser();
        Alert alert = mockAlert(mockChannel());
        Pageable pageable = PageRequest.of(0, 50);

        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of(1L));
        when(alertRepository.findByDeviceIdIn(eq(List.of(1L)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(alert)));

        Page<AlertResponse> result = alertService.getAllAlerts("EMP001", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSensorValue()).isEqualTo(95.0);
        assertThat(result.getContent().get(0).getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
    }

    @Test
    void getAllAlerts_returns_empty_when_no_accessible_devices() {
        User user = mockUser();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of());

        Page<AlertResponse> result = alertService.getAllAlerts("EMP001", PageRequest.of(0, 50));

        assertThat(result).isEmpty();
        verify(alertRepository, never()).findByDeviceIdIn(any(), any());
    }

    @Test
    void getAllAlerts_returns_empty_when_no_alerts_for_accessible_devices() {
        User user = mockUser();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of(1L));
        when(alertRepository.findByDeviceIdIn(eq(List.of(1L)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<AlertResponse> result = alertService.getAllAlerts("EMP001", PageRequest.of(0, 50));

        assertThat(result).isEmpty();
    }

    @Test
    void getAllAlerts_fail_user_not_found() {
        when(accessControlService.getUser("NOTEXIST"))
                .thenThrow(new IllegalArgumentException("존재하지 않는 사원번호예요"));

        assertThrows(IllegalArgumentException.class,
                () -> alertService.getAllAlerts("NOTEXIST", PageRequest.of(0, 50)));
    }

    // ── 채널 스코프 조회 ────────────────────────────────────────────────

    @Test
    void getAlertsByChannel_success() {
        User user = mockUser();
        SensorChannel channel = mockChannel();
        Alert alert = mockAlert(channel);

        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getChannel(1L)).thenReturn(channel);
        doNothing().when(accessControlService).assertCanAccessChannel(user, channel);
        when(alertRepository.findByChannelIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(alert));

        List<AlertResponse> result = alertService.getAlertsByChannel("EMP001", 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSensorValue()).isEqualTo(95.0);
    }

    @Test
    void getAlertsByChannel_fail_channel_not_found() {
        User user = mockUser();
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getChannel(99L))
                .thenThrow(new IllegalArgumentException("존재하지 않는 채널이에요 - channelId: 99"));

        assertThrows(IllegalArgumentException.class,
                () -> alertService.getAlertsByChannel("EMP001", 99L));
    }

    @Test
    void getAlertsByChannel_fail_access_denied() {
        User user = mockUser();
        SensorChannel channel = mockChannel();

        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getChannel(1L)).thenReturn(channel);
        doThrow(new AccessDeniedException("접근 권한이 없어요"))
                .when(accessControlService).assertCanAccessChannel(user, channel);

        assertThrows(AccessDeniedException.class,
                () -> alertService.getAlertsByChannel("EMP001", 1L));
    }

    @Test
    void getDailyCount_maps_rows_and_uses_clock_for_window() {
        User user = mockUser();
        SensorChannel channel = mockChannel();

        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getChannel(1L)).thenReturn(channel);
        doNothing().when(accessControlService).assertCanAccessChannel(user, channel);
        Instant expectedStart = FIXED.minus(java.time.Duration.ofDays(7));
        when(alertRepository.findDailyCountByChannelId(eq(1L), eq(expectedStart)))
                .thenReturn(List.of(
                        new Object[]{"2026-07-15", 3L},
                        new Object[]{"2026-07-16", 5L}));

        List<DailyAlertCountResponse> result = alertService.getDailyCount("EMP001", 1L, 7);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDate()).isEqualTo("2026-07-15");
        assertThat(result.get(0).getCount()).isEqualTo(3L);
        assertThat(result.get(1).getCount()).isEqualTo(5L);
    }

    @Test
    void getDailyCount_fail_access_denied() {
        User user = mockUser();
        SensorChannel channel = mockChannel();

        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(accessControlService.getChannel(1L)).thenReturn(channel);
        doThrow(new AccessDeniedException("접근 권한이 없어요"))
                .when(accessControlService).assertCanAccessChannel(user, channel);

        assertThrows(AccessDeniedException.class,
                () -> alertService.getDailyCount("EMP001", 1L, 7));
        verify(alertRepository, never()).findDailyCountByChannelId(any(), any());
    }
}
