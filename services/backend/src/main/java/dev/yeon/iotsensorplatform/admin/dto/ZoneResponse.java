package dev.yeon.iotsensorplatform.admin.dto;

import dev.yeon.iotsensorplatform.factory.entity.Zone;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ZoneResponse {
    private final Long id;
    private final Long factoryId;
    private final String factoryName;
    private final String name;
    private final String description;
    private final LocalDateTime createdAt;

    public ZoneResponse(Zone zone) {
        this.id = zone.getId();
        this.factoryId = zone.getFactory().getId();
        this.factoryName = zone.getFactory().getName();
        this.name = zone.getName();
        this.description = zone.getDescription();
        this.createdAt = zone.getCreatedAt();
    }
}
