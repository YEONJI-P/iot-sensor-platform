package dev.yeon.iotsensorplatform.sensordata.service;

import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.kafka.SensorDataProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataService {
    private final SensorDataProducer sensorDataProducer;
    private final DeviceRepository deviceRepository;
    public void receive(SensorDataRequest sensorDataRequest) {
        deviceRepository.findById(sensorDataRequest.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 장치에요 - deviceId: " + sensorDataRequest.getDeviceId()
                ));
        sensorDataProducer.send(sensorDataRequest);
    }
}
