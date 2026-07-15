package dev.yeon.iotsensorplatform.sse;

/**
 * 실시간 전송 대상 이벤트. 트랜잭션 커밋 후 SSE로 흘려보내기 위해 사용한다.
 * deviceId는 접근 범위 필터에 쓰인다(null이면 전체 대상).
 */
public record SseBroadcastEvent(String event, Long deviceId, Object payload) {
}
