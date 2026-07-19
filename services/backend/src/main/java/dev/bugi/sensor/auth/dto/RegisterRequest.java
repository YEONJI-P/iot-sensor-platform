package dev.bugi.sensor.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "가입 신청 요청")
public class RegisterRequest {

    @NotBlank
    @Schema(description = "사원번호 (로그인 ID)", example = "EMP001")
    private String employeeId;

    @NotBlank
    @Schema(description = "이름", example = "홍길동")
    private String name;

    @NotBlank
    @Schema(description = "비밀번호", example = "password123!")
    private String password;

    @Schema(description = "이메일 (선택)", example = "hong@company.com")
    private String email;

    @NotNull(message = "가입할 공장은 필수예요")
    @Positive(message = "공장 ID는 1 이상이어야 해요")
    @Schema(description = "가입할 공장 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long factoryId;
}
