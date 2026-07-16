package dev.bugi.sensor.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    // @CreatedDate 감사 필드(Alert.createdAt, SensorData.recordedAt)를 Instant(UTC)로 채운다.
    // 주입한 Clock을 쓰므로 테스트에서 시각을 고정할 수 있다.
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
