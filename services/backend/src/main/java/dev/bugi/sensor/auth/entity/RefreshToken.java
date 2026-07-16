package dev.bugi.sensor.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Refresh Token 서버측 저장소.
 * 사원번호(employeeId)가 PK — 1인당 1토큰이며 회전 시 같은 행을 덮어쓴다.
 * 회전 재사용(탈취) 탐지·서버측 로그아웃·만료를 이 테이블 하나로 담당한다.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private String employeeId;

    @Column(nullable = false)
    private String token;

    @Column(nullable = false)
    private Instant expiresAt;

    public RefreshToken(String employeeId, String token, Instant expiresAt) {
        this.employeeId = employeeId;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public void rotate(String token, Instant expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
