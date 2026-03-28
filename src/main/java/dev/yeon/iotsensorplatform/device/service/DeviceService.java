package dev.yeon.iotsensorplatform.device.service;

import dev.yeon.iotsensorplatform.device.dto.DeviceRegisterRequest;
import dev.yeon.iotsensorplatform.device.dto.DeviceResponse;
import dev.yeon.iotsensorplatform.device.dto.DeviceUpdateRequest;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public DeviceResponse register(DeviceRegisterRequest request, String employeeId) {
        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));
        Device device = Device.builder()
                .user(user)
                .name(request.getName())
                .type(request.getType())
                .location(request.getLocation())
                .thresholdValue(request.getThresholdValue())
                .build();
        deviceRepository.save(device);
        return DeviceResponse.from(device);
    }

    @Transactional
    public DeviceResponse update(Long deviceId, DeviceUpdateRequest request, String employeeId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("장치 정보가 존재하지 않아요"));
        if (!device.getUser().getEmployeeId().equals(employeeId)) {
            throw new IllegalArgumentException("본인 장치만 수정할 수 있어요");
        }
        device.update(request.getName(), request.getType(), request.getLocation(), request.getThresholdValue());
        deviceRepository.save(device);
        return DeviceResponse.from(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getMyDevices(String employeeId) {
        return deviceRepository.findAllByUserEmployeeId(employeeId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long deviceId, String employeeId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("장치 정보가 존재하지 않아요"));
        if (!device.getUser().getEmployeeId().equals(employeeId)) {
            throw new IllegalArgumentException("본인 장치만 제거할 수 있어요");
        }
        deviceRepository.delete(device);
    }
}
