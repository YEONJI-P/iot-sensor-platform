package dev.yeon.iotsensorplatform.user.entity;

import dev.yeon.iotsensorplatform.organization.entity.Organization;
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

    private String department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

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
                String department, Organization organization, Role role, UserStatus status) {
        this.employeeId = employeeId;
        this.name = name;
        this.email = email;
        this.password = password;
        this.department = department;
        this.organization = organization;
        this.role = role;
        this.status = status;
    }

    public void approve() {
        this.status = UserStatus.ACTIVE;
    }

    public void reject() {
        this.status = UserStatus.REJECTED;
    }
}
