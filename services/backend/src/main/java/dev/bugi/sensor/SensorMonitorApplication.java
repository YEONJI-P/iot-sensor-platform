package dev.bugi.sensor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SensorMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SensorMonitorApplication.class, args);
    }

}
