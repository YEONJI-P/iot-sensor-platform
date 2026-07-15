package dev.bugi.sensor.global.config;

import dev.bugi.sensor.auth.filter.JwtFilter;
import dev.bugi.sensor.global.security.CustomAccessDeniedHandler;
import dev.bugi.sensor.global.security.CustomAuthenticationEntryPoint;
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
                        // 정적 자산은 공개(인증은 JS가 JWT로 API 호출 시 수행). 페이지 접근 제어는 프론트가 역할로 처리.
                        .requestMatchers("/", "/index.html", "/dashboard.html", "/console.html")
                        .permitAll()
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico")
                        .permitAll()
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/sensor-data","/auth/refresh")
                        .permitAll()

                        // 공장 관리 — SYSTEM_ADMIN만
                        .requestMatchers("/admin/factories/**")
                        .hasRole("SYSTEM_ADMIN")

                        // 구역 관리 — SYSTEM_ADMIN, FACTORY_ADMIN
                        .requestMatchers("/admin/zones/**")
                        .hasAnyRole("SYSTEM_ADMIN", "FACTORY_ADMIN")

                        // 기존 어드민 — SYSTEM_ADMIN, FACTORY_ADMIN
                        .requestMatchers("/admin/**")
                        .hasAnyRole("SYSTEM_ADMIN", "FACTORY_ADMIN")

                        // SSE 스트림 — 토큰 쿼리파라미터로 컨트롤러에서 직접 인증
                        .requestMatchers(HttpMethod.GET, "/dashboard/stream")
                        .permitAll()

                        // 센서 데이터 조회 — FACTORY_ADMIN 제외 (운영 데이터 불필요)
                        .requestMatchers(HttpMethod.GET, "/sensor-data", "/sensor-data/**")
                        .hasAnyRole("SYSTEM_ADMIN", "MEMBER", "VIEWER")

                        // 알림 조회 — FACTORY_ADMIN 제외
                        .requestMatchers(HttpMethod.GET, "/alerts", "/alerts/**")
                        .hasAnyRole("SYSTEM_ADMIN", "MEMBER", "VIEWER")

                        // 장치 변경(등록·수정·삭제) — 장치 운영 주체(SYSTEM_ADMIN, MEMBER)만.
                        // FACTORY_ADMIN은 사용자·구역 관리 담당이라 장치 운영에서 제외. VIEWER는 읽기 전용.
                        .requestMatchers(HttpMethod.POST, "/devices")
                        .hasAnyRole("SYSTEM_ADMIN", "MEMBER")
                        .requestMatchers(HttpMethod.PUT, "/devices/**")
                        .hasAnyRole("SYSTEM_ADMIN", "MEMBER")
                        .requestMatchers(HttpMethod.DELETE, "/devices/**")
                        .hasAnyRole("SYSTEM_ADMIN", "MEMBER")

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
