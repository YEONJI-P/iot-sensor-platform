package dev.bugi.sensor.sensordata.dto;

/**
 * 부분 실패한 채널 판독. reason 예: UNKNOWN_CHANNEL, NULL_VALUE.
 */
public record RejectedReading(String channelCode, String reason) {
}
