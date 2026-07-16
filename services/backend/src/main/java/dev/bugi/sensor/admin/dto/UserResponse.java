package dev.bugi.sensor.admin.dto;

import dev.bugi.sensor.user.entity.Role;
import dev.bugi.sensor.user.entity.User;
import dev.bugi.sensor.user.entity.UserStatus;
import lombok.Getter;

import java.time.Instant;

@Getter
public class UserResponse {

    private final Long id;
    private final String employeeId;
    private final String name;
    private final String email;
    private final Long factoryId;
    private final String factoryName;
    private final Role role;
    private final UserStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public UserResponse(User user) {
        this.id = user.getId();
        this.employeeId = user.getEmployeeId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.factoryId = user.getFactory() != null ? user.getFactory().getId() : null;
        this.factoryName = user.getFactory() != null ? user.getFactory().getName() : null;
        this.role = user.getRole();
        this.status = user.getStatus();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }
}
