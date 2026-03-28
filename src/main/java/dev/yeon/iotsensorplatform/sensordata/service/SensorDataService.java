package dev.yeon.iotsensorplatform.sensordata.service;

import dev.yeon.iotsensorplatform.alert.entity.Alert;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataResponse;
import dev.yeon.iotsensorplatform.sensordata.entity.SensorData;
import dev.yeon.iotsensorplatform.sensordata.kafka.SensorDataProducer;
import dev.yeon.iotsensorplatform.sensordata.repository.SensorDataRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SensorDataService {
    private final Optional<SensorDataProducer> sensorDataProducer;
    private final DeviceRepository deviceRepository;
    private final SensorDataRepository sensorDataRepository;
    private final AlertRepository alertRepository;

    @Transactional
    public void receive(SensorDataRequest sensorDataRequest) {
        Device device = deviceRepository.findById(sensorDataRequest.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 장치에요 - deviceId: " + sensorDataRequest.getDeviceId()
                ));
        if(sensorDataProducer.isPresent()) {
            sensorDataProducer.get().send(sensorDataRequest);
        }else{
            SensorData sensorData = SensorData.builder()
                    .device(device)
                    .value(sensorDataRequest.getValue()).build();
            sensorDataRepository.save(sensorData);
            if (sensorDataRequest.getValue() > device.getThresholdValue()) {
                Alert alert = Alert.builder().device(device).sensorValue(sensorDataRequest.getValue()).thresholdValue(device.getThresholdValue())
                        .message(String.format("[%s] 임계값 초과! 현재값: %.1f, 임계값: %.1f ", device.getName(),sensorDataRequest.getValue(),device.getThresholdValue()))
                        .build();
                alertRepository.save(alert);
            }
        }

    }

    // save는 kafka측에서
    // just read
    @Transactional(readOnly=true)
    public List<SensorDataResponse> getAllSensorData(String employeeId){
        return sensorDataRepository.findAllByDeviceUserEmployeeIdOrderByRecordedAtDesc(employeeId)
                .stream().map(SensorDataResponse::from).toList();
    }
    // 특정 device의 sensor 값만 조회
    @Transactional(readOnly=true)
    public List<SensorDataResponse> getAllSensorDataByDeviceId(String employeeId, Long deviceId){
        Device device = deviceRepository.findById(deviceId).orElseThrow(()->new IllegalArgumentException("존재하지 않는 장치에요 - deviceId: " + deviceId));
        if(!device.getUser().getEmployeeId().equals(employeeId)) {
            throw new IllegalArgumentException("본인 장치만 조회할 수 있어요");
        }
        return sensorDataRepository.findAllByDeviceIdOrderByRecordedAtDesc(deviceId)
                .stream().map(SensorDataResponse::from).toList();
    }

}
