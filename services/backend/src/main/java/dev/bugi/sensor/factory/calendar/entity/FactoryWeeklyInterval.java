package dev.bugi.sensor.factory.calendar.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "factory_weekly_interval", uniqueConstraints = @UniqueConstraint(
        name = "uk_factory_weekly_interval", columnNames = {"factory_id", "iso_day", "start_minute", "end_minute"}))
public class FactoryWeeklyInterval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private FactoryOperatingCalendar calendar;

    private short isoDay;
    private short startMinute;
    private short endMinute;

    public FactoryWeeklyInterval(FactoryOperatingCalendar calendar, int isoDay, int startMinute, int endMinute) {
        this.calendar = calendar;
        this.isoDay = (short) isoDay;
        this.startMinute = (short) startMinute;
        this.endMinute = (short) endMinute;
    }
}
