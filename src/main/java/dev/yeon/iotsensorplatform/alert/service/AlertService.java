package dev.yeon.iotsensorplatform.alert.service;

import dev.yeon.iotsensorplatform.alert.dto.AlertResponse;
import dev.yeon.iotsensorplatform.alert.dto.DailyAlertCountResponse;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
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

    @Transactional(readOnly = true)
    public List<AlertResponse> getAllAlerts(String employeeId) {
        return alertRepository.findAllByDeviceUserEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAllAlertsByDeviceId(String employeeId, Long deviceId) {
        Device device = getOwnedDevice(employeeId, deviceId);
        return alertRepository.findAllByDeviceIdOrderByCreatedAtDesc(device.getId())
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getRecentAlerts(String employeeId, Long deviceId, int limit) {
        getOwnedDevice(employeeId, deviceId);
        return alertRepository.findRecentByDeviceId(deviceId, limit)
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DailyAlertCountResponse> getDailyCount(String employeeId, Long deviceId, int days) {
        getOwnedDevice(employeeId, deviceId);
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

    private Device getOwnedDevice(String employeeId, Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장치예요 - deviceId: " + deviceId));
        if (!device.getUser().getEmployeeId().equals(employeeId)) {
            throw new IllegalArgumentException("본인 장치만 조회할 수 있어요");
        }
        return device;
    }
}
