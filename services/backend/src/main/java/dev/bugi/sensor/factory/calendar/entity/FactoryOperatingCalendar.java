package dev.bugi.sensor.factory.calendar.entity;

import dev.bugi.sensor.factory.entity.Factory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "factory_operating_calendar")
public class FactoryOperatingCalendar {

    @Id
    private Long factoryId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "factory_id")
    private Factory factory;

    @Column(name = "timezone_id", nullable = false, length = 64)
    private String timezoneId;

    @Column(nullable = false)
    private int resumeGraceSeconds;

    @Version
    @Column(nullable = false)
    private long revision;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public FactoryOperatingCalendar(Factory factory, String timezoneId, int resumeGraceSeconds, Instant now) {
        this.factory = factory;
        this.timezoneId = timezoneId;
        this.resumeGraceSeconds = resumeGraceSeconds;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void replaceSettings(String timezoneId, int resumeGraceSeconds, Instant now) {
        this.timezoneId = timezoneId;
        this.resumeGraceSeconds = resumeGraceSeconds;
        // 같은 fixed Clock tick에서 동일 값을 다시 저장해도 aggregate child 교체는 새 revision이다.
        this.updatedAt = updatedAt != null && !now.isAfter(updatedAt) ? updatedAt.plusNanos(1_000) : now;
    }
}
