package dev.yeon.iotsensorplatform.alert.service;

import dev.yeon.iotsensorplatform.alert.dto.AlertResponse;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {
    // just Read API
    // user 에게 등록된 device 전체 alert
    // 특정 device alert (user 제한)

    private final AlertRepository alertRepository;
    private final DeviceRepository deviceRepository;

    @Transactional(readOnly=true)
    public List<AlertResponse> getAllAlerts(String employeeId){
        return alertRepository.findAllByDeviceUserEmployeeIdOrderByCreatedAtDesc(employeeId).stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public List<AlertResponse> getAllAlertsByDeviceId(String employeeId, Long deviceId){
        Device device = deviceRepository.findById(deviceId).orElseThrow(()->new IllegalArgumentException("존재하지 않는 장치에요 - deviceId: " + deviceId));
        if(!device.getUser().getEmployeeId().equals(employeeId)){
            throw new IllegalArgumentException("본인 장치만 조회할 수 있어요");
        }
        return alertRepository.findAllByDeviceIdOrderByCreatedAtDesc(device.getId())
                .stream().map(AlertResponse::from).toList();
    }
}
