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
@Table(name = "factory_date_override_interval", uniqueConstraints = @UniqueConstraint(
        name = "uk_factory_date_override_interval", columnNames = {"override_id", "start_minute", "end_minute"}))
public class FactoryDateOverrideInterval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "override_id", nullable = false)
    private FactoryDateOverride override;

    private short startMinute;
    private short endMinute;

    public FactoryDateOverrideInterval(FactoryDateOverride override, int startMinute, int endMinute) {
        this.override = override;
        this.startMinute = (short) startMinute;
        this.endMinute = (short) endMinute;
    }
}
