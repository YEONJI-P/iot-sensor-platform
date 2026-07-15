package dev.yeon.iotsensorplatform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "로그인 요청")
public class LoginRequest {

    @NotBlank
    @Schema(description = "사원번호", example = "EMP001")
    private String employeeId;

    @NotBlank
    @Schema(description = "비밀번호", example = "password123!")
    private String password;
}
