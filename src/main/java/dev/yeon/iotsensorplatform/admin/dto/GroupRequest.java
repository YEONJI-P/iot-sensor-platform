package dev.yeon.iotsensorplatform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GroupRequest {
    @NotNull
    private Long organizationId;
    @NotBlank
    private String name;
    private String description;
}
