package dev.yeon.iotsensorplatform.factory.entity;

import dev.yeon.iotsensorplatform.user.entity.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "zone_users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"zone_id", "user_id"}))
@EntityListeners(AuditingEntityListener.class)
public class ZoneUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ZoneUser(Zone zone, User user) {
        this.zone = zone;
        this.user = user;
    }
}
