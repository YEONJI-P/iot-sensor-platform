package dev.bugi.sensor.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 현재 시각 주입점. 모든 now() 호출은 이 Clock(UTC)을 거친다.
 * Instant.now() 직접 호출 대신 이 빈을 받아 clock.instant()로 얻으면 테스트에서 고정 가능.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
