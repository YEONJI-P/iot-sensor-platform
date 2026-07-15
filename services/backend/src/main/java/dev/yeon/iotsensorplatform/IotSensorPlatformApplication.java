package dev.yeon.iotsensorplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IotSensorPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IotSensorPlatformApplication.class, args);
    }

}
