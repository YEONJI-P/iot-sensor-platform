package dev.yeon.iotsensorplatform.admin.dto;

import dev.yeon.iotsensorplatform.factory.entity.Factory;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class FactoryResponse {
    private final Long id;
    private final String name;
    private final String description;
    private final LocalDateTime createdAt;

    public FactoryResponse(Factory org) {
        this.id = org.getId();
        this.name = org.getName();
        this.description = org.getDescription();
        this.createdAt = org.getCreatedAt();
    }
}
