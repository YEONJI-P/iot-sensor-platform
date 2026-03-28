package dev.yeon.iotsensorplatform.admin.dto;

import dev.yeon.iotsensorplatform.organization.entity.Organization;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OrgResponse {
    private final Long id;
    private final String name;
    private final String description;
    private final LocalDateTime createdAt;

    public OrgResponse(Organization org) {
        this.id = org.getId();
        this.name = org.getName();
        this.description = org.getDescription();
        this.createdAt = org.getCreatedAt();
    }
}
