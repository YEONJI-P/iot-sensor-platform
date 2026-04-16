package dev.yeon.iotsensorplatform.sensordata.service;

import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataResponse;
import dev.yeon.iotsensorplatform.sensordata.kafka.SensorDataProducer;
import dev.yeon.iotsensorplatform.sensordata.repository.SensorDataRepository;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SensorDataService {

    private final SensorDataProducer sensorDataProducer;
    private final DeviceRepository deviceRepository;
    private final SensorDataRepository sensorDataRepository;
    private final AccessControlService accessControlService;
    private final UserRepository userRepository;

    @Transactional
    public void receive(SensorDataRequest request) {
        Device device = deviceRepository.findById(request.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 장치에요 - deviceId: " + request.getDeviceId()));

        sensorDataProducer.send(request);
    }

    @Transactional(readOnly = true)
    public List<SensorDataResponse> getAllSensorData(String employeeId) {
        User user = getUser(employeeId);
        List<Long> deviceIds = accessControlService.getAccessibleDeviceIds(user);
        if (deviceIds.isEmpty()) return List.of();
        return sensorDataRepository.findAllByDeviceIdInOrderByRecordedAtDesc(deviceIds)
                .stream().map(SensorDataResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SensorDataResponse> getAllSensorDataByDeviceId(String employeeId, Long deviceId) {
        User user = getUser(employeeId);
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장치예요 - deviceId: " + deviceId));
        accessControlService.assertCanAccessDevice(user, device);
        return sensorDataRepository.findAllByDeviceIdOrderByRecordedAtDesc(deviceId)
                .stream().map(SensorDataResponse::from).toList();
    }

    private User getUser(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));
    }
}
