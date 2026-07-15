package dev.yeon.iotsensorplatform.sse;

import dev.yeon.iotsensorplatform.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 대시보드 실시간 스트림 구독 엔드포인트.
 *
 * EventSource는 Authorization 헤더를 실을 수 없어, JWT를 {@code ?token=<JWT>}
 * 쿼리파라미터로 받아 컨트롤러에서 직접 검증한다. 이 엔드포인트는 SecurityConfig
 * 에서 permitAll 로 열려 있으므로 인증은 여기서 책임진다.
 */
@RestController
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;
    private final JwtUtil jwtUtil;

    @GetMapping(value = "/dashboard/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(value = "token", required = false) String token) {
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
        return sseService.subscribe();
    }
}
