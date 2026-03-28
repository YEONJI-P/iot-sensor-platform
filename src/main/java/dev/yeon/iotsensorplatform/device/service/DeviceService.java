package dev.yeon.iotsensorplatform.device.service;

import dev.yeon.iotsensorplatform.device.dto.DeviceRegisterRequest;
import dev.yeon.iotsensorplatform.device.dto.DeviceResponse;
import dev.yeon.iotsensorplatform.device.dto.DeviceUpdateRequest;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.global.service.AccessControlService;
import dev.yeon.iotsensorplatform.organization.entity.OrgGroup;
import dev.yeon.iotsensorplatform.organization.repository.OrgGroupRepository;
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
    private final OrgGroupRepository orgGroupRepository;
    private final AccessControlService accessControlService;

    @Transactional
    public DeviceResponse register(DeviceRegisterRequest request, String employeeId) {
        User user = getUser(employeeId);
        OrgGroup group = orgGroupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 그룹이에요"));
        accessControlService.assertCanManageGroup(user, group);

        Device device = Device.builder()
                .group(group)
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
        User user = getUser(employeeId);
        Device device = getDevice(deviceId);
        accessControlService.assertCanAccessDevice(user, device);

        device.update(request.getName(), request.getType(), request.getLocation(), request.getThresholdValue());
        deviceRepository.save(device);
        return DeviceResponse.from(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getMyDevices(String employeeId) {
        User user = getUser(employeeId);
        return accessControlService.getAccessibleDevices(user).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long deviceId, String employeeId) {
        User user = getUser(employeeId);
        Device device = getDevice(deviceId);
        accessControlService.assertCanAccessDevice(user, device);
        deviceRepository.delete(device);
    }

    private User getUser(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));
    }

    private Device getDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("장치 정보가 존재하지 않아요"));
    }
}
