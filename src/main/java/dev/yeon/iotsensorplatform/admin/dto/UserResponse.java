package dev.yeon.iotsensorplatform.admin.dto;

import dev.yeon.iotsensorplatform.user.entity.Role;
import dev.yeon.iotsensorplatform.user.entity.User;
import dev.yeon.iotsensorplatform.user.entity.UserStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserResponse {

    private final Long id;
    private final String employeeId;
    private final String name;
    private final String email;
    private final String department;
    private final Long organizationId;
    private final Role role;
    private final UserStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public UserResponse(User user) {
        this.id = user.getId();
        this.employeeId = user.getEmployeeId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.department = user.getDepartment();
        this.organizationId = user.getOrganizationId();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }
}
