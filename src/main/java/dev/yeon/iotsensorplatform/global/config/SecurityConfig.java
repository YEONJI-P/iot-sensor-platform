package dev.yeon.iotsensorplatform.global.config;

import dev.yeon.iotsensorplatform.auth.filter.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화 (JWT 방식은 불필요)
                .csrf(AbstractHttpConfigurer::disable)

                // 세션 사용 안 함 (JWT 쓰니까 STATELESS)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // URL별 접근 권한 설정
                // 대상지정 > 동작 메서드 세트 반복
                // 위에서 아래로 break
                .authorizeHttpRequests(auth -> auth
                        // 회원가입, 로그인은 누구나 접근 가능
                        .requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login","/sensor-data").permitAll()
                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
                )

                // JwtFilter를 Security 기본 필터 앞에 등록
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 비밀번호 암호화 (BCrypt 단방향 해시 - 복호화 불가, 비교만 가능)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}