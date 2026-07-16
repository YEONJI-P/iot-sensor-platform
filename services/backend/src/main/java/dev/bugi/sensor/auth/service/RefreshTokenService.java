package dev.bugi.sensor.auth.service;

import dev.bugi.sensor.auth.entity.RefreshToken;
import dev.bugi.sensor.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    private static final Duration TTL = Duration.ofDays(7);

    @Transactional
    public void save(String employeeId, String refreshToken) {
        Instant expiresAt = clock.instant().plus(TTL);
        refreshTokenRepository.findById(employeeId)
                .ifPresentOrElse(
                        existing -> existing.rotate(refreshToken, expiresAt),
                        () -> refreshTokenRepository.save(new RefreshToken(employeeId, refreshToken, expiresAt))
                );
    }

    @Transactional(readOnly = true)
    public String get(String employeeId) {
        return refreshTokenRepository.findById(employeeId)
                .filter(t -> !t.isExpired(clock.instant()))
                .map(RefreshToken::getToken)
                .orElse(null);
    }

    @Transactional
    public void delete(String employeeId) {
        refreshTokenRepository.deleteById(employeeId);
    }

    @Transactional(readOnly = true)
    public boolean validate(String employeeId, String refreshToken) {
        String stored = get(employeeId);
        return stored != null && stored.equals(refreshToken);
    }
}
