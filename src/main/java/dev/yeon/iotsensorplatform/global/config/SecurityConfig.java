package dev.yeon.iotsensorplatform.global.config;

import dev.yeon.iotsensorplatform.auth.filter.JwtFilter;
import dev.yeon.iotsensorplatform.global.security.CustomAccessDeniedHandler;
import dev.yeon.iotsensorplatform.global.security.CustomAuthenticationEntryPoint;
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
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // 정적 파일 & Swagger & 인증 불필요 API
                        .requestMatchers("/", "/index.html", "/dashboard.html")
                        .permitAll()
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/sensor-data","/auth/refresh")
                        .permitAll()

                        // 공장 관리 — SYSTEM_ADMIN만
                        .requestMatchers("/admin/factories/**")
                        .hasRole("SYSTEM_ADMIN")

                        // 구역 관리 — SYSTEM_ADMIN, ORG_ADMIN
                        .requestMatchers("/admin/zones/**")
                        .hasAnyRole("SYSTEM_ADMIN", "ORG_ADMIN")

                        // 기존 어드민 — SYSTEM_ADMIN, ORG_ADMIN
                        .requestMatchers("/admin/**")
                        .hasAnyRole("SYSTEM_ADMIN", "ORG_ADMIN")

                        // 대시보드 API — 운영 역할(MEMBER, VIEWER)
                        .requestMatchers("/dashboard/**")
                        .hasAnyRole("MEMBER", "VIEWER")

                        // 센서 데이터 조회 — ORG_ADMIN 제외 (운영 데이터 불필요)
                        .requestMatchers(HttpMethod.GET, "/sensor-data", "/sensor-data/**")
                        .hasAnyRole("SYSTEM_ADMIN", "MEMBER", "VIEWER")

                        // 알림 조회 — ORG_ADMIN 제외
                        .requestMatchers(HttpMethod.GET, "/alerts", "/alerts/**")
                        .hasAnyRole("SYSTEM_ADMIN", "MEMBER", "VIEWER")

                        // 나머지 인증 필요
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
