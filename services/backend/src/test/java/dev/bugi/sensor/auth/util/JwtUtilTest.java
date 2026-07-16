package dev.bugi.sensor.auth.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-for-ci-only-must-be-at-least-32-bytes";
    private static final long EXPIRATION = Duration.ofHours(1).toMillis();
    private static final long REFRESH_EXPIRATION = Duration.ofDays(7).toMillis();

    private JwtUtil jwtUtilAt(Instant now, long expiration, long refreshExpiration) {
        return new JwtUtil(SECRET, expiration, refreshExpiration,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    // jjwt 파서는 만료 검증을 실제 시스템 시각으로 하므로, 유효 경로 테스트는
    // 발급 시각을 현재로 고정해 exp 가 실제 미래에 오도록 한다.
    private JwtUtil jwtUtil() {
        return jwtUtilAt(Instant.now(), EXPIRATION, REFRESH_EXPIRATION);
    }

    @Test
    void access_token_carries_subject_and_role() {
        JwtUtil jwtUtil = jwtUtil();

        String token = jwtUtil.createToken("EMP001", "MEMBER");

        assertThat(jwtUtil.getEmployeeId(token)).isEqualTo("EMP001");
        assertThat(jwtUtil.getRole(token)).isEqualTo("MEMBER");
    }

    @Test
    void access_token_passes_access_type_check() {
        JwtUtil jwtUtil = jwtUtil();

        String token = jwtUtil.createToken("EMP001", "MEMBER");

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.validateAccessToken(token)).isTrue();
    }

    @Test
    void refresh_token_is_valid_but_rejected_as_access_token() {
        JwtUtil jwtUtil = jwtUtil();

        String refresh = jwtUtil.createRefreshToken("EMP001");

        // 서명·만료는 유효하지만 type=refresh 이므로 access 검사는 실패해야 한다.
        assertThat(jwtUtil.validateToken(refresh)).isTrue();
        assertThat(jwtUtil.validateAccessToken(refresh)).isFalse();
    }

    @Test
    void token_expired_relative_to_injected_clock_is_rejected() {
        // 발급 시각을 과거로 고정하고 만료를 1초로 주면 exp 가 실제 현재보다 앞선다.
        // validateToken 이 false 면 만료 계산이 주입된 Clock 기준으로 이뤄졌다는 뜻.
        JwtUtil pastIssuer = jwtUtilAt(Instant.parse("2000-01-01T00:00:00Z"), 1000L, 1000L);

        String token = pastIssuer.createToken("EMP001", "MEMBER");

        assertThat(pastIssuer.validateToken(token)).isFalse();
        assertThat(pastIssuer.validateAccessToken(token)).isFalse();
    }

    @Test
    void token_not_yet_expired_within_window_is_accepted() {
        // 발급 시각을 현재로 두고 만료 창을 크게 주면 아직 유효해야 한다.
        JwtUtil jwtUtil = jwtUtilAt(Instant.now(), Duration.ofHours(1).toMillis(), REFRESH_EXPIRATION);

        String token = jwtUtil.createToken("EMP001", "MEMBER");

        assertThat(jwtUtil.validateToken(token)).isTrue();
    }
}
