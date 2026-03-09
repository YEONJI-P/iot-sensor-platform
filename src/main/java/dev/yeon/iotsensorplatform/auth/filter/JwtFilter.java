package dev.yeon.iotsensorplatform.auth.filter;

import dev.yeon.iotsensorplatform.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Header에서 토큰 꺼내기
        String token = resolveToken(request);

        // 2. 토큰 유효성 검증
        if (token != null && jwtUtil.validateToken(token)) {

            // 3. 토큰에서 email 꺼내기
            String email = jwtUtil.getEmail(token);

            // 4. SecurityContext에 인증정보 저장
            // principal=email 현재 인증받는주체를 뜻함,
            // credentials 은 인증방법으로 대부분 PW 이나 여기서는 token 검증했으므로 null
            // authorities SPRING 규칙 > ROLE_ 접두사를 따라 권한 할당
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 5. 토큰 없어도 다음으로 통과 (차단은 SecurityConfig가 담당)
        filterChain.doFilter(request, response);
    }

    // "Bearer 토큰값" 에서 토큰만 추출
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            // Bearer 문자열 삭제
            return bearer.substring(7);
        }
        return null;
    }
}