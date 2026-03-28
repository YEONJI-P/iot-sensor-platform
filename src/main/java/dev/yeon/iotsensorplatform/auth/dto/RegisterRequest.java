package dev.yeon.iotsensorplatform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    @Schema(description = "부서 (선택)", example = "개발팀")
    private String department;

    @Schema(description = "조직 ID (선택)", example = "1")
    private Long organizationId;
}
