package dev.bugi.sensor.auth.service;

import dev.bugi.sensor.auth.entity.RefreshToken;
import dev.bugi.sensor.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void save_inserts_new_token_when_absent() {
        when(refreshTokenRepository.findById("EMP001")).thenReturn(Optional.empty());

        refreshTokenService.save("EMP001", "token123");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEmployeeId()).isEqualTo("EMP001");
        assertThat(captor.getValue().getToken()).isEqualTo("token123");
    }

    @Test
    void save_rotates_existing_token_in_place() {
        RefreshToken existing = new RefreshToken("EMP001", "old_token", LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findById("EMP001")).thenReturn(Optional.of(existing));

        refreshTokenService.save("EMP001", "new_token");

        // 기존 행을 덮어쓰므로 save(insert) 호출 없이 엔티티만 회전
        verify(refreshTokenRepository, never()).save(any());
        assertThat(existing.getToken()).isEqualTo("new_token");
    }

    @Test
    void get_returns_stored_token() {
        RefreshToken stored = new RefreshToken("EMP001", "token123", LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findById("EMP001")).thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.get("EMP001")).isEqualTo("token123");
    }

    @Test
    void get_returns_null_when_expired() {
        RefreshToken stored = new RefreshToken("EMP001", "token123", LocalDateTime.now().minusSeconds(1));
        when(refreshTokenRepository.findById("EMP001")).thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.get("EMP001")).isNull();
    }

    @Test
    void get_returns_null_when_absent() {
        when(refreshTokenRepository.findById("EMP001")).thenReturn(Optional.empty());

        assertThat(refreshTokenService.get("EMP001")).isNull();
    }

    @Test
    void delete_removes_key() {
        refreshTokenService.delete("EMP001");

        verify(refreshTokenRepository, times(1)).deleteById("EMP001");
    }

    @Test
    void validate_returns_true_when_token_matches() {
        RefreshToken stored = new RefreshToken("EMP001", "token123", LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findById("EMP001")).thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.validate("EMP001", "token123")).isTrue();
    }

    @Test
    void validate_returns_false_when_token_mismatch() {
        RefreshToken stored = new RefreshToken("EMP001", "other_token", LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findById("EMP001")).thenReturn(Optional.of(stored));

        assertThat(refreshTokenService.validate("EMP001", "token123")).isFalse();
    }

    @Test
    void validate_returns_false_when_no_stored_token() {
        when(refreshTokenRepository.findById("EMP001")).thenReturn(Optional.empty());

        assertThat(refreshTokenService.validate("EMP001", "token123")).isFalse();
    }
}
