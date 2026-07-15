package dev.yeon.iotsensorplatform.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 대시보드 실시간 전송(SSE) 관리.
 * 구독자 emitter를 보관하고, 수신 이벤트를 연결된 모든 클라이언트로 broadcast 한다.
 * 메시지 버스 없이 인프로세스 메서드 호출로만 동작한다.
 */
@Slf4j
@Service
public class SseService {

    private static final long TIMEOUT = 30 * 60 * 1000L; // 30분

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** 연결된 모든 구독자에게 이벤트를 전송한다. 실패한 emitter는 제거한다. */
    public void broadcast(String event, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                emitter.completeWithError(e);
                emitters.remove(emitter);
            }
        }
    }
}
