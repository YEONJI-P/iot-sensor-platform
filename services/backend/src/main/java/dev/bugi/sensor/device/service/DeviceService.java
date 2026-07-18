package dev.bugi.sensor.device.service;

import dev.bugi.sensor.device.dto.DeviceRegisterRequest;
import dev.bugi.sensor.device.dto.DeviceResponse;
import dev.bugi.sensor.device.dto.DeviceUpdateRequest;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.factory.entity.Zone;
import dev.bugi.sensor.factory.repository.ZoneRepository;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ZoneRepository zoneRepository;
    private final AccessControlService accessControlService;

    @Transactional
    public DeviceResponse register(DeviceRegisterRequest request, String employeeId) {
        User user = getUser(employeeId);
        accessControlService.assertCanMutateDevice(user);
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구역이에요"));
        accessControlService.assertCanManageZone(user, zone);

        Device device = Device.builder()
                .zone(zone)
                .code(request.getCode())
                .name(request.getName())
                .location(request.getLocation())
                .expectedIntervalSeconds(request.getExpectedIntervalSeconds())
                .build();
        deviceRepository.save(device);
        return DeviceResponse.from(device);
    }

    @Transactional
    public DeviceResponse update(Long deviceId, DeviceUpdateRequest request, String employeeId) {
        User user = getUser(employeeId);
        accessControlService.assertCanMutateDevice(user);
        Device device = getDevice(deviceId);
        accessControlService.assertCanAccessDevice(user, device);

        device.update(request.getName(), request.getLocation(), request.getExpectedIntervalSeconds());
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
        accessControlService.assertCanMutateDevice(user);
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
