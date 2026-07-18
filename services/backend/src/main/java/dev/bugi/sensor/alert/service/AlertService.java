package dev.bugi.sensor.alert.service;

import dev.bugi.sensor.alert.dto.AlertResponse;
import dev.bugi.sensor.alert.dto.DailyAlertCountResponse;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.repository.SensorChannelRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final int MAX_RECENT = 500; // 채널별 조회 상한

    private final AlertRepository alertRepository;
    private final SensorChannelRepository sensorChannelRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AlertResponse> getAllAlerts(String employeeId, Pageable pageable) {
        User user = getUser(employeeId);
        List<Long> deviceIds = accessControlService.getAccessibleDeviceIds(user);
        if (deviceIds.isEmpty()) return Page.empty(pageable);
        return alertRepository.findByDeviceIdIn(deviceIds, pageable)
                .map(AlertResponse::from);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByChannel(String employeeId, Long channelId) {
        User user = getUser(employeeId);
        SensorChannel channel = getChannel(channelId);
        accessControlService.assertCanAccessChannel(user, channel);
        // 무제한 로드 방지 — 최근 N건만 반환.
        return alertRepository
                .findByChannelIdOrderByCreatedAtDesc(channelId, PageRequest.of(0, MAX_RECENT))
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getRecentAlerts(String employeeId, Long channelId, int limit) {
        User user = getUser(employeeId);
        SensorChannel channel = getChannel(channelId);
        accessControlService.assertCanAccessChannel(user, channel);
        return alertRepository.findRecentByChannelId(channelId, limit)
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DailyAlertCountResponse> getDailyCount(String employeeId, Long channelId, int days) {
        User user = getUser(employeeId);
        SensorChannel channel = getChannel(channelId);
        accessControlService.assertCanAccessChannel(user, channel);

        Instant startDate = clock.instant().minus(Duration.ofDays(days));
        List<Object[]> rows = alertRepository.findDailyCountByChannelId(channelId, startDate);

        List<DailyAlertCountResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String date = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            result.add(new DailyAlertCountResponse(date, count));
        }
        return result;
    }

    private User getUser(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));
    }

    private SensorChannel getChannel(Long channelId) {
        return sensorChannelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채널이에요 - channelId: " + channelId));
    }
}
