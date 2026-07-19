package dev.bugi.sensor.auth.dto;

import dev.bugi.sensor.factory.entity.Factory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "가입 시 선택할 공장")
public record FactoryOptionResponse(
        Long id,
        String name
) {
    public static FactoryOptionResponse from(Factory factory) {
        return new FactoryOptionResponse(factory.getId(), factory.getName());
    }
}
