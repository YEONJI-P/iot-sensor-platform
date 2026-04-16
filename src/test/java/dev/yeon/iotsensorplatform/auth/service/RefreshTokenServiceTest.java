package dev.yeon.iotsensorplatform.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void save_stores_token_with_ttl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        refreshTokenService.save("EMP001", "token123");

        verify(valueOperations, times(1))
                .set("refresh:EMP001", "token123", Duration.ofDays(7));
    }

    @Test
    void get_returns_stored_token() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:EMP001")).thenReturn("token123");
        String result = refreshTokenService.get("EMP001");

        assertThat(result).isEqualTo("token123");
    }

    @Test
    void delete_removes_key() {
        refreshTokenService.delete("EMP001");

        verify(redisTemplate, times(1)).delete("refresh:EMP001");
    }

    @Test
    void validate_returns_true_when_token_matches(){
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:EMP001")).thenReturn("token123");

        assertThat(refreshTokenService.validate("EMP001","token123")).isTrue();
    }

    @Test
    void validate_returns_fail_when_token_mismatch(){
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:EMP001")).thenReturn("other_token");

        assertThat(refreshTokenService.validate("EMP001","token123")).isFalse();
    }

    @Test
    void validate_returns_false_when_no_stored_token(){
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:EMP001")).thenReturn(null);

        assertThat(refreshTokenService.validate("EMP001","token123")).isFalse();
    }

}