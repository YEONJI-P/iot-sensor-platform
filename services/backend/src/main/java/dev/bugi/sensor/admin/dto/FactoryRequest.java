package dev.bugi.sensor.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FactoryRequest {
    @NotBlank
    private String name;
    private String description;
}
