package dev.bugi.sensor.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 현재 시각 공급원. 시각 처리 규칙(UTC 저장)에 따라 UTC 고정 Clock을 주입한다.
 * Instant.now() 직접 호출 대신 이 빈을 받아 clock.instant()로 얻으면 테스트에서 고정 가능.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
