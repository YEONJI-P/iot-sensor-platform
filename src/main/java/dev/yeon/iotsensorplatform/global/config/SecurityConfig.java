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
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // 정적 리소스 & Swagger
                        .requestMatchers("/", "/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**")
                        .permitAll()
                        // HTML 파일 자체는 열리게 (API 호출 시 JWT로 제어)
                        .requestMatchers("/simulator.html", "/dashboard.html")
                        .permitAll()
                        // 인증 불필요 API
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/sensor-data")
                        .permitAll()
                        // 어드민: USER_ADMIN 이상
                        .requestMatchers("/admin/**")
                        .hasAnyRole("SUPER_ADMIN", "USER_ADMIN")
                        // 나머지 전부 인증 필요 (/simulator/**, /devices/**, /alerts/**, /sensor-data GET 등)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
