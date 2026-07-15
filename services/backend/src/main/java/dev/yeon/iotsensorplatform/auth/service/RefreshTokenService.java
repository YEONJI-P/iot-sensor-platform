package dev.yeon.iotsensorplatform.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "refresh:";
    private static final Duration TTL = Duration.ofDays(7);

        public void save(String employeeId, String refreshToken){
        redisTemplate.opsForValue().set(KEY_PREFIX + employeeId,refreshToken,TTL);
    }
    public String get(String employeeId){
        return redisTemplate.opsForValue().get(KEY_PREFIX+employeeId);
    }

    public void delete(String employeeId){
        redisTemplate.delete(KEY_PREFIX+employeeId);
    }
    public boolean validate(String employeeId,String refreshToken){
        String stored = get(employeeId);
        return stored !=null && stored.equals(refreshToken);
    }
}
