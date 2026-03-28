package dev.yeon.iotsensorplatform.alert.service;

import dev.yeon.iotsensorplatform.alert.dto.AlertResponse;
import dev.yeon.iotsensorplatform.alert.dto.DailyAlertCountResponse;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public List<AlertResponse> getAllAlerts(String employeeId) {
        User user = getUser(employeeId);
        List<Long> deviceIds = accessControlService.getAccessibleDeviceIds(user);
        if (deviceIds.isEmpty()) return List.of();
        return alertRepository.findAllByDeviceIdInOrderByCreatedAtDesc(deviceIds)
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAllAlertsByDeviceId(String employeeId, Long deviceId) {
        User user = getUser(employeeId);
        Device device = getDevice(deviceId);
        accessControlService.assertCanAccessDevice(user, device);
        return alertRepository.findAllByDeviceIdOrderByCreatedAtDesc(device.getId())
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getRecentAlerts(String employeeId, Long deviceId, int limit) {
        User user = getUser(employeeId);
        Device device = getDevice(deviceId);
        accessControlService.assertCanAccessDevice(user, device);
        return alertRepository.findRecentByDeviceId(deviceId, limit)
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DailyAlertCountResponse> getDailyCount(String employeeId, Long deviceId, int days) {
        User user = getUser(employeeId);
        Device device = getDevice(deviceId);
        accessControlService.assertCanAccessDevice(user, device);

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = alertRepository.findDailyCountByDeviceId(deviceId, startDate);

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

    private Device getDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장치예요 - deviceId: " + deviceId));
    }
}
