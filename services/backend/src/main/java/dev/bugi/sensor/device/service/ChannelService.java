package dev.bugi.sensor.device.service;

import dev.bugi.sensor.device.dto.ChannelCreateRequest;
import dev.bugi.sensor.device.dto.ChannelResponse;
import dev.bugi.sensor.device.dto.ChannelUpdateRequest;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.device.repository.SensorChannelRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final DeviceRepository deviceRepository;
    private final SensorChannelRepository sensorChannelRepository;
    private final AccessControlService accessControlService;

    /**
     * 접근 가능한 채널 목록. deviceId 가 있으면 그 장치의 채널만(접근 검사 후), 없으면 접근 범위 전체.
     */
    @Transactional(readOnly = true)
    public List<ChannelResponse> getMyChannels(String employeeId, Long deviceId) {
        User user = accessControlService.getUser(employeeId);
        if (deviceId != null) {
            Device device = getDevice(deviceId);
            accessControlService.assertCanAccessDevice(user, device);
            return sensorChannelRepository.findByDeviceIdInWithDeviceAndZone(List.of(deviceId))
                    .stream().map(ChannelResponse::from).toList();
        }
        List<Long> deviceIds = accessControlService.getAccessibleDeviceIds(user);
        if (deviceIds.isEmpty()) {
            return List.of();
        }
        return sensorChannelRepository.findByDeviceIdInWithDeviceAndZone(deviceIds)
                .stream().map(ChannelResponse::from).toList();
    }

    @Transactional
    public ChannelResponse createChannel(Long deviceId, ChannelCreateRequest request, String employeeId) {
        User user = accessControlService.getUser(employeeId);
        accessControlService.assertCanMutateDevice(user);
        Device device = getDevice(deviceId);
        accessControlService.assertCanAccessDevice(user, device);

        SensorChannel channel = SensorChannel.builder()
                .device(device)
                .code(request.getCode())
                .unit(request.getUnit())
                .quantityKind(request.getQuantityKind())
                .thresholdValue(request.getThresholdValue())
                .thresholdDirection(request.getThresholdDirection())
                .build();
        sensorChannelRepository.save(channel);
        return ChannelResponse.from(channel);
    }

    @Transactional
    public ChannelResponse updateChannel(Long channelId, ChannelUpdateRequest request, String employeeId) {
        User user = accessControlService.getUser(employeeId);
        accessControlService.assertCanMutateDevice(user);
        SensorChannel channel = accessControlService.getChannel(channelId);
        accessControlService.assertCanAccessChannel(user, channel);

        channel.update(request.getUnit(), request.getQuantityKind(),
                request.getThresholdValue(), request.getThresholdDirection());
        sensorChannelRepository.save(channel);
        return ChannelResponse.from(channel);
    }

    private Device getDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장치예요 - deviceId: " + deviceId));
    }
}
