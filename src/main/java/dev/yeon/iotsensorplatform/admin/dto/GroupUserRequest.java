package dev.yeon.iotsensorplatform.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GroupUserRequest {
    @NotNull
    private Long userId;
}
