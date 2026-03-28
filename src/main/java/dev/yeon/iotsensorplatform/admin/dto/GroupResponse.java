package dev.yeon.iotsensorplatform.admin.dto;

import dev.yeon.iotsensorplatform.organization.entity.OrgGroup;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GroupResponse {
    private final Long id;
    private final Long organizationId;
    private final String organizationName;
    private final String name;
    private final String description;
    private final LocalDateTime createdAt;

    public GroupResponse(OrgGroup group) {
        this.id = group.getId();
        this.organizationId = group.getOrganization().getId();
        this.organizationName = group.getOrganization().getName();
        this.name = group.getName();
        this.description = group.getDescription();
        this.createdAt = group.getCreatedAt();
    }
}
