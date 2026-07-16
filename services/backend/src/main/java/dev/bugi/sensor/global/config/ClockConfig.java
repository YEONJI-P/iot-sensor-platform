package dev.bugi.sensor.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 현재 시각 주입점. 모든 now() 호출은 이 Clock(UTC)을 거친다.
 * 테스트에서 Clock.fixed(...) 로 시각을 고정할 수 있다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
