package dev.bugi.sensor.factory.calendar.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "factory_date_override", uniqueConstraints = @UniqueConstraint(
        name = "uk_factory_date_override", columnNames = {"factory_id", "local_date"}))
public class FactoryDateOverride {

    public enum Kind { CLOSED, OPEN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id", nullable = false)
    private FactoryOperatingCalendar calendar;

    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    public FactoryDateOverride(FactoryOperatingCalendar calendar, LocalDate localDate, Kind kind) {
        this.calendar = calendar;
        this.localDate = localDate;
        this.kind = kind;
    }
}
