package dev.bugi.sensor.dashboard.service;

import dev.bugi.sensor.dashboard.dto.DashboardOverviewResponse;
import dev.bugi.sensor.dashboard.dto.DashboardOverviewResponse.Freshness;
import dev.bugi.sensor.device.entity.ChannelStatus;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.DeviceStatus;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import dev.bugi.sensor.device.repository.ChannelStatusRepository;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.device.repository.DeviceStatusRepository;
import dev.bugi.sensor.device.repository.SensorChannelRepository;
import dev.bugi.sensor.factory.entity.Factory;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.sensordata.anomaly.ThresholdDetector;
import dev.bugi.sensor.sensordata.repository.SensorReadingRepository;
import dev.bugi.sensor.sensordata.repository.SensorReadingRepository.LatestReadingProjection;
import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardOverviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Mock AccessControlService accessControlService;
    @Mock DeviceRepository deviceRepository;
    @Mock DeviceStatusRepository deviceStatusRepository;
    @Mock SensorChannelRepository sensorChannelRepository;
    @Mock ChannelStatusRepository channelStatusRepository;
    @Mock SensorReadingRepository sensorReadingRepository;
    @Spy ThresholdDetector thresholdDetector = new ThresholdDetector();
    @Spy Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks DashboardOverviewService service;

    @Test
    void overview는_접근가능_device만_grouping하고_status_latest_threshold를_합성한다() {
        User user = mock(User.class);
        Factory factory = mock(Factory.class);
        Zone zone = mock(Zone.class);
        Device device = mock(Device.class);
        DeviceStatus deviceStatus = mock(DeviceStatus.class);
        SensorChannel channel = mock(SensorChannel.class);
        ChannelStatus channelStatus = mock(ChannelStatus.class);
        LatestReadingProjection latest = mock(LatestReadingProjection.class);

        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(user.getRole()).thenReturn(Role.MEMBER);
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of(7L));
        when(deviceRepository.findOverviewDevicesByIdIn(List.of(7L))).thenReturn(List.of(device));

        when(factory.getId()).thenReturn(1L);
        when(factory.getName()).thenReturn("공장 A");
        when(zone.getId()).thenReturn(2L);
        when(zone.getName()).thenReturn("조립 구역");
        when(zone.getFactory()).thenReturn(factory);
        when(device.getId()).thenReturn(7L);
        when(device.getZone()).thenReturn(zone);
        when(device.getCode()).thenReturn("PRESS-7");
        when(device.getName()).thenReturn("프레스 7호");
        when(device.getLocation()).thenReturn("북측");
        when(device.getExpectedIntervalSeconds()).thenReturn(30);

        when(deviceStatus.getDeviceId()).thenReturn(7L);
        when(deviceStatus.getLastSeenAt()).thenReturn(NOW.minusSeconds(61));
        when(deviceStatusRepository.findAllById(List.of(7L))).thenReturn(List.of(deviceStatus));

        when(channel.getId()).thenReturn(11L);
        when(channel.getDevice()).thenReturn(device);
        when(channel.getCode()).thenReturn("temperature");
        when(channel.getUnit()).thenReturn("°C");
        when(channel.getQuantityKind()).thenReturn("temperature");
        when(channel.getThresholdValue()).thenReturn(80.0);
        when(channel.getThresholdDirection()).thenReturn(ThresholdDirection.ABOVE);
        when(sensorChannelRepository.findByDeviceIdInWithDeviceAndZone(List.of(7L)))
                .thenReturn(List.of(channel));

        when(channelStatus.getChannelId()).thenReturn(11L);
        when(channelStatus.isInAlarm()).thenReturn(true);
        when(channelStatus.getLastAlertAt()).thenReturn(NOW.minusSeconds(5));
        when(channelStatusRepository.findAllById(List.of(11L))).thenReturn(List.of(channelStatus));

        when(latest.getChannelId()).thenReturn(11L);
        when(latest.getValue()).thenReturn(81.5);
        when(latest.getObservedAt()).thenReturn(NOW.minusSeconds(2));
        when(latest.getReceivedAt()).thenReturn(NOW.minusSeconds(1));
        when(sensorReadingRepository.findLatestByChannelIds(List.of(11L))).thenReturn(List.of(latest));

        DashboardOverviewResponse result = service.getOverview("EMP001");

        assertThat(result.generatedAt()).isEqualTo(NOW);
        assertThat(result.factories()).singleElement().satisfies(factoryResult -> {
            assertThat(factoryResult.name()).isEqualTo("공장 A");
            assertThat(factoryResult.zones()).singleElement().satisfies(zoneResult -> {
                assertThat(zoneResult.name()).isEqualTo("조립 구역");
                assertThat(zoneResult.devices()).singleElement().satisfies(deviceResult -> {
                    assertThat(deviceResult.id()).isEqualTo(7L);
                    assertThat(deviceResult.freshness()).isEqualTo(Freshness.STALE);
                    assertThat(deviceResult.lastSeenAt()).isEqualTo(NOW.minusSeconds(61));
                    assertThat(deviceResult.currentAlarmCount()).isEqualTo(1);
                    assertThat(deviceResult.channels()).singleElement().satisfies(channelResult -> {
                        assertThat(channelResult.latestValue()).isEqualTo(81.5);
                        assertThat(channelResult.anomaly()).isTrue();
                        assertThat(channelResult.inAlarm()).isTrue();
                        assertThat(channelResult.thresholdValue()).isEqualTo(80.0);
                        assertThat(channelResult.thresholdDirection()).isEqualTo(ThresholdDirection.ABOVE);
                    });
                });
            });
        });
        verify(deviceRepository).findOverviewDevicesByIdIn(List.of(7L));
    }

    @Test
    void overview는_접근가능_device가_없으면_빈_group만_반환한다() {
        User user = mock(User.class);
        when(accessControlService.getUser("EMP001")).thenReturn(user);
        when(user.getRole()).thenReturn(Role.VIEWER);
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of());

        DashboardOverviewResponse result = service.getOverview("EMP001");

        assertThat(result.generatedAt()).isEqualTo(NOW);
        assertThat(result.factories()).isEmpty();
        verifyNoInteractions(deviceRepository, deviceStatusRepository, sensorChannelRepository,
                channelStatusRepository, sensorReadingRepository);
    }

    @Test
    void overview는_FACTORY_ADMIN을_허용한다() {
        User user = mock(User.class);
        when(accessControlService.getUser("ADMIN")).thenReturn(user);
        when(user.getRole()).thenReturn(Role.FACTORY_ADMIN);
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of());

        DashboardOverviewResponse result = service.getOverview("ADMIN");

        assertThat(result.factories()).isEmpty();
        verifyNoInteractions(deviceRepository, deviceStatusRepository, sensorChannelRepository,
                channelStatusRepository, sensorReadingRepository);
    }

    @Test
    void overview는_SYSTEM_ADMIN을_허용한다() {
        User user = mock(User.class);
        when(accessControlService.getUser("SYSTEM")).thenReturn(user);
        when(user.getRole()).thenReturn(Role.SYSTEM_ADMIN);
        when(accessControlService.getAccessibleDeviceIds(user)).thenReturn(List.of());

        DashboardOverviewResponse result = service.getOverview("SYSTEM");

        assertThat(result.factories()).isEmpty();
    }

    @Test
    void freshness는_감시여부와_수신이력과_기대주기_경계를_구분한다() {
        assertThat(DashboardOverviewService.freshness(null, null, NOW)).isEqualTo(Freshness.NOT_MONITORED);
        assertThat(DashboardOverviewService.freshness(0, null, NOW)).isEqualTo(Freshness.NOT_MONITORED);
        assertThat(DashboardOverviewService.freshness(30, null, NOW)).isEqualTo(Freshness.NEVER_SEEN);
        assertThat(DashboardOverviewService.freshness(30, NOW.minusSeconds(60), NOW)).isEqualTo(Freshness.ONLINE);
        assertThat(DashboardOverviewService.freshness(30, NOW.minusSeconds(61), NOW)).isEqualTo(Freshness.STALE);
        assertThat(DashboardOverviewService.freshness(30, NOW.plusSeconds(1), NOW)).isEqualTo(Freshness.ONLINE);
    }
}
