package dev.yeon.iotsensorplatform.sensordata.service;

import dev.yeon.iotsensorplatform.alert.entity.Alert;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataResponse;
import dev.yeon.iotsensorplatform.sensordata.entity.SensorData;
import dev.yeon.iotsensorplatform.sensordata.kafka.SensorDataProducer;
import dev.yeon.iotsensorplatform.sensordata.repository.SensorDataRepository;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SensorDataService {

    private final Optional<SensorDataProducer> sensorDataProducer;
    private final DeviceRepository deviceRepository;
    private final SensorDataRepository sensorDataRepository;
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;

    // 외부 장치 또는 시뮬레이터 → AUTO, createdBy=null
    @Transactional
    public void receive(SensorDataRequest request) {
        receive(request, null);
    }

    // 사용자 수동 입력 → MANUAL, createdBy=user
    @Transactional
    public void receive(SensorDataRequest request, String employeeId) {
        Device device = deviceRepository.findById(request.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 장치에요 - deviceId: " + request.getDeviceId()));

        User createdBy = null;
        SensorData.InputType inputType = SensorData.InputType.AUTO;
        if (employeeId != null) {
            createdBy = userRepository.findByEmployeeId(employeeId).orElse(null);
            inputType = SensorData.InputType.MANUAL;
        }

        if (sensorDataProducer.isPresent()) {
            sensorDataProducer.get().send(request);
        } else {
            SensorData sensorData = SensorData.builder()
                    .device(device)
                    .value(request.getValue())
                    .inputType(inputType)
                    .createdBy(createdBy)
                    .build();
            sensorDataRepository.save(sensorData);

            if (device.getThresholdValue() != null && request.getValue() > device.getThresholdValue()) {
                Alert alert = Alert.builder()
                        .device(device)
                        .sensorValue(request.getValue())
                        .thresholdValue(device.getThresholdValue())
                        .message(String.format("[%s] 임계값 초과! 현재값: %.1f, 임계값: %.1f",
                                device.getName(), request.getValue(), device.getThresholdValue()))
                        .build();
                alertRepository.save(alert);
            }
        }
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
