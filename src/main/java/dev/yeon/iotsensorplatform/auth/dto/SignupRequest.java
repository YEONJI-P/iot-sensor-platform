package dev.yeon.iotsensorplatform.auth.dto;

import dev.yeon.iotsensorplatform.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {
    @NotBlank
    @Email
    private String email;
    @NotBlank
    private String password;

    // Service에서 암호화된 비밀번호 받아서 Entity 생성
    public User toEntity(String encodedPassword) {
        return User.builder()
                .email(this.email)
                .password(encodedPassword)
                .role(User.Role.USER)
                .build();
    }
}