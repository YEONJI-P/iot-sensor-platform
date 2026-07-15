package dev.yeon.iotsensorplatform.user.entity;

import dev.yeon.iotsensorplatform.factory.entity.Factory;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String employeeId;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;

    private String password;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factory_id")
    private Factory factory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public User(String employeeId, String name, String email, String password,
                Factory factory, Role role, UserStatus status) {
        this.employeeId = employeeId;
        this.name = name;
        this.email = email;
        this.password = password;
        this.factory = factory;
        this.role = role;
        this.status = status;
    }

    public void approve(Role role) {
        this.status = UserStatus.ACTIVE;
        this.role = role;
    }

    public void assignFactory(Factory factory) {
        this.factory = factory;
    }

    public void reject() {
        this.status = UserStatus.REJECTED;
    }
}
