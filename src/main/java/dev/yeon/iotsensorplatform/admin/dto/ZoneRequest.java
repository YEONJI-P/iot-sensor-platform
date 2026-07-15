package dev.yeon.iotsensorplatform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ZoneRequest {
    @NotNull
    private Long factoryId;
    @NotBlank
    private String name;
    private String description;
}
