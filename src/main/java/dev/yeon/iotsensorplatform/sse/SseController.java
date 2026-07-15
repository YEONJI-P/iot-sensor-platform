package dev.yeon.iotsensorplatform.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 대시보드 실시간 스트림 구독 엔드포인트.
 *
 * 주의(골격): EventSource는 Authorization 헤더를 실을 수 없어, 현재 매처
 * (/dashboard/** 인증 필요)와 바로 연동되지 않는다. 토큰 쿼리파라미터 등
 * SSE 인증 방식은 후속 작업으로 남긴다.
 */
@RestController
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @GetMapping(value = "/dashboard/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseService.subscribe();
    }
}
