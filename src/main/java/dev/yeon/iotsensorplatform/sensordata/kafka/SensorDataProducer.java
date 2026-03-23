package dev.yeon.iotsensorplatform.sensordata.kafka;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.yeon.iotsensorplatform.sensordata.dto.SensorDataRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Profile("!prod") // prod 환경이 아닐때만 Bean 생성
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataProducer {
    private final KafkaTemplate<String,String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.sensor-data:sensor-data}")
    private String topic;

    public void send(SensorDataRequest request) {
        try{
            String message = objectMapper.writeValueAsString(request);
            kafkaTemplate.send(topic, message);
            log.info("Kafka 메시지 발행 완료 - topic: {}, message: {}", topic, message);
        } catch(JsonProcessingException e){
            log.error("Kafka 메시지 변환 실패: {}",e.getMessage());
            throw new RuntimeException("메시지 변환 실패", e);
        }
    }

}
