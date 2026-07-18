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
import dev.bugi.sensor.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final SensorChannelRepository sensorChannelRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public List<ChannelResponse> getMyChannels(String employeeId) {
        User user = getUser(employeeId);
        List<Long> deviceIds = accessControlService.getAccessibleDeviceIds(user);
        if (deviceIds.isEmpty()) {
            return List.of();
        }
        return sensorChannelRepository.findByDeviceIdInWithDeviceAndZone(deviceIds)
                .stream().map(ChannelResponse::from).toList();
    }

    @Transactional
    public ChannelResponse createChannel(Long deviceId, ChannelCreateRequest request, String employeeId) {
        User user = getUser(employeeId);
        accessControlService.assertCanMutateDevice(user);
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장치예요 - deviceId: " + deviceId));
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
        User user = getUser(employeeId);
        accessControlService.assertCanMutateDevice(user);
        SensorChannel channel = sensorChannelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채널이에요 - channelId: " + channelId));
        accessControlService.assertCanAccessChannel(user, channel);

        channel.update(request.getUnit(), request.getQuantityKind(),
                request.getThresholdValue(), request.getThresholdDirection());
        sensorChannelRepository.save(channel);
        return ChannelResponse.from(channel);
    }

    private User getUser(String employeeId) {
        return userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호예요"));
    }
}
