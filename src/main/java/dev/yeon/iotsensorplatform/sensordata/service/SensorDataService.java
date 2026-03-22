package dev.yeon.iotsensorplatform.sensordata.service;

import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataResponse;
import dev.yeon.iotsensorplatform.sensordata.kafka.SensorDataProducer;
import dev.yeon.iotsensorplatform.sensordata.repository.SensorDataRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SensorDataService {
    private final SensorDataProducer sensorDataProducer;
    private final DeviceRepository deviceRepository;
    private final SensorDataRepository sensorDataRepository;

    @Transactional
    public void receive(SensorDataRequest sensorDataRequest) {
        deviceRepository.findById(sensorDataRequest.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 장치에요 - deviceId: " + sensorDataRequest.getDeviceId()
                ));
        sensorDataProducer.send(sensorDataRequest);
    }
    // save는 kafka측에서
    // just read
    @Transactional(readOnly=true)
    public List<SensorDataResponse> getAllSensorData(String email){
        return sensorDataRepository.findAllByDeviceUserEmailOrderByRecordedAtDesc(email)
                .stream().map(SensorDataResponse::from).toList();
    }
    // 특정 device의 sensor 값만 조회
    @Transactional(readOnly=true)
    public List<SensorDataResponse> getAllSensorDataByDeviceId(String email, Long deviceId){
        Device device = deviceRepository.findById(deviceId).orElseThrow(()->new IllegalArgumentException("존재하지 않는 장치에요 - deviceId: " + deviceId));
        // device가 내 device인지 확인
        if(!device.getUser().getEmail().equals(email)) {
            throw new IllegalArgumentException("본인 장치만 조회할 수 있어요");
        }
        return sensorDataRepository.findAllByDeviceIdOrderByRecordedAtDesc(deviceId)
                .stream().map(SensorDataResponse::from).toList();
    }

}
