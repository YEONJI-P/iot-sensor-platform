package dev.yeon.iotsensorplatform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrgRequest {
    @NotBlank
    private String name;
    private String description;
}
