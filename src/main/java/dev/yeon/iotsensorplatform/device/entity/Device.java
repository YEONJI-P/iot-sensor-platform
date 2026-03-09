package dev.yeon.iotsensorplatform.device.entity;

import dev.yeon.iotsensorplatform.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    private String name;
    @Enumerated(EnumType.STRING)
    private DeviceType type;
    private String location;
    private Double thresholdValue;

    @Builder
    public Device(User user, String name, DeviceType type, String location, Double thresholdValue) {
        this.user = user;
        this.name = name;
        this.type = type;
        this.location = location;
        this.thresholdValue = thresholdValue;
    }

    public void update(String name, DeviceType type, String location, Double thresholdValue) {
        this.name = name;
        this.type = type;
        this.location = location;
        this.thresholdValue = thresholdValue;
    }

    public enum DeviceType {
        TEMPERATURE, VIBRATION, ILLUMINANCE
    }
}