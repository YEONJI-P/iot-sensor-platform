package dev.yeon.iotsensorplatform.sensordata.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.yeon.iotsensorplatform.alert.entity.Alert;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.device.entity.Device;
import dev.yeon.iotsensorplatform.device.repository.DeviceRepository;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import dev.yeon.iotsensorplatform.sensordata.entity.SensorData;
import dev.yeon.iotsensorplatform.sensordata.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataConsumer {
    private final SensorDataRepository sensorDataRepository;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    // 공통 - 메시지 -> 센서데이터 리퀘스트 객체 변환
    private SensorDataRequest parseMessage(String message){
        try{
            return objectMapper.readValue(message, SensorDataRequest.class);
        }catch (JsonProcessingException e) {
            log.error("Kafka 메시지 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    // 곹통 - device 체크
    private Device findDevice(Long deviceId){
        return deviceRepository.findById(deviceId)
                .orElseGet(() -> {
                    log.error("존재하지 않는 장치 - deviceId: {}", deviceId);
                    return null;  // null 반환
                });
    }

    @KafkaListener(topics= "${kafka.topic.sensor-data:sensor-data}",groupId = "iot-sensor-group")
    public void save(String message){

        SensorDataRequest request = parseMessage(message);
        if(request == null) return;

        Device device = findDevice(request.getDeviceId());
        if (device == null) return;

        SensorData sensorData = SensorData.builder().device(device).value(request.getValue()).build();
        sensorDataRepository.save(sensorData);
        log.info("센서 데이터 저장 완료 - deviceId: {}, value: {}", request.getDeviceId(), request.getValue());

        if(request.getValue()>device.getThresholdValue()){
            Alert alert = Alert.builder()
                    .device(device)
                    .sensorValue(request.getValue())
                    .thresholdValue(device.getThresholdValue())
                    .message(String.format("[%s] 임계값 초과! 현재값: %.1f, 임계값: %.1f ",
                            device.getName(),request.getValue(),device.getThresholdValue()))
                    .build();
            alertRepository.save(alert);
            log.warn("Alert 생성  - device: {}, value: {}", device.getName(), request.getValue());
        }
    }

}
