package dev.bugi.sensor.sensordata.service;

import dev.bugi.sensor.alert.dto.AlertResponse;
import dev.bugi.sensor.alert.entity.Alert;
import dev.bugi.sensor.alert.entity.AlertSeverity;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.device.entity.ChannelStatus;
import dev.bugi.sensor.device.entity.Device;
import dev.bugi.sensor.device.entity.DeviceStatus;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.device.entity.SensorChannel.ThresholdDirection;
import dev.bugi.sensor.device.repository.ChannelStatusRepository;
import dev.bugi.sensor.device.repository.DeviceRepository;
import dev.bugi.sensor.device.repository.DeviceStatusRepository;
import dev.bugi.sensor.device.repository.SensorChannelRepository;
import dev.bugi.sensor.global.service.AccessControlService;
import dev.bugi.sensor.sensordata.anomaly.AnomalyDetector;
import dev.bugi.sensor.sensordata.dto.BatchIngestRequest;
import dev.bugi.sensor.sensordata.dto.BatchIngestResult;
import dev.bugi.sensor.sensordata.dto.BatchSsePayload;
import dev.bugi.sensor.sensordata.dto.ReadingResponse;
import dev.bugi.sensor.sensordata.dto.RejectedReading;
import dev.bugi.sensor.sensordata.entity.MeasurementBatch;
import dev.bugi.sensor.sensordata.entity.SensorReading;
import dev.bugi.sensor.sensordata.failure.FailedReading;
import dev.bugi.sensor.sensordata.failure.FailedReadingRepository;
import dev.bugi.sensor.sensordata.repository.MeasurementBatchRepository;
import dev.bugi.sensor.sensordata.repository.SensorReadingRepository;
import dev.bugi.sensor.sse.SseBroadcastEvent;
import dev.bugi.sensor.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataService {

    private static final int MAX_RECENT = 500; // 채널별 조회 상한

    // 재발화 방지 여유(데드밴드): ABOVE 는 임계*RELEASE_RATIO 아래로 내려와야 알람 해제.
    // 임계값 근처에서 값이 떨려도 껐다 켰다 반복하지 않게 한다.
    private static final double RELEASE_RATIO = 0.97;
    // ABOVE 는 임계*CRITICAL_RATIO 이하 초과는 WARNING, 초과하면 CRITICAL.
    private static final double CRITICAL_RATIO = 1.1;
    // 재발화 지연(시간 쿨다운): 직전 발화 후 이 시간 안에는 다시 발화하지 않는다(산발 스파이크 억제).
    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    private final DeviceRepository deviceRepository;
    private final DeviceStatusRepository deviceStatusRepository;
    private final SensorChannelRepository sensorChannelRepository;
    private final ChannelStatusRepository channelStatusRepository;
    private final MeasurementBatchRepository measurementBatchRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final AlertRepository alertRepository;
    private final FailedReadingRepository failedReadingRepository;
    private final AnomalyDetector anomalyDetector;
    private final ApplicationEventPublisher eventPublisher;
    private final AccessControlService accessControlService;
    private final Clock clock;

    /**
     * 한 batch 수신. 부분 실패(미지 채널·null 값)는 예외가 아니라 결과로 표현한다
     * — 예외로 롤백하면 failed_reading 적재까지 사라진다. 컨트롤러가 outcome 을 HTTP 로 매핑한다.
     */
    @Transactional
    public BatchIngestResult receive(BatchIngestRequest request) {
        Instant receivedAt = clock.instant();
        Instant observedAt = request.getObservedAt() != null ? request.getObservedAt() : receivedAt;
        String deviceCode = request.getDeviceCode();

        Device device = deviceRepository.findByCode(deviceCode).orElse(null);
        if (device == null) {
            // 조용히 버리지 않고 실패 요약 1행 적재(데이터 안 옴 신호 소스). → 404
            failedReadingRepository.save(FailedReading.builder()
                    .deviceCode(deviceCode).reason("DEVICE_NOT_FOUND").build());
            log.warn("수신 실패(장치 없음) - deviceCode: {}", deviceCode);
            return BatchIngestResult.deviceNotFound(deviceCode, receivedAt);
        }

        // 채널 Map 1회 로드 후 known/unknown 분할.
        Map<String, SensorChannel> channelsByCode = new HashMap<>();
        for (SensorChannel channel : sensorChannelRepository.findByDeviceId(device.getId())) {
            channelsByCode.put(channel.getCode(), channel);
        }

        List<RejectedReading> rejected = new ArrayList<>();
        List<FailedReading> failures = new ArrayList<>();
        Map<SensorChannel, Double> known = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : request.getMeasurements().entrySet()) {
            String code = entry.getKey();
            Double value = entry.getValue();
            SensorChannel channel = channelsByCode.get(code);
            if (channel == null) {
                rejected.add(new RejectedReading(code, "UNKNOWN_CHANNEL"));
                // deviceId 를 채워 freshness 원인진단(countByDeviceIdAndCreatedAtAfter)이 실패를 셀 수 있게 한다.
                failures.add(FailedReading.builder()
                        .deviceId(device.getId()).deviceCode(deviceCode).channelCode(code).value(value)
                        .reason("UNKNOWN_CHANNEL").build());
            } else if (value == null) {
                rejected.add(new RejectedReading(code, "NULL_VALUE"));
                failures.add(FailedReading.builder()
                        .deviceId(device.getId()).deviceCode(deviceCode).channelCode(code)
                        .reason("NULL_VALUE").build());
            } else {
                known.put(channel, value);
            }
        }
        // 거부된 판독은 한 번에 적재한다(개별 save round-trip 회피).
        if (!failures.isEmpty()) {
            failedReadingRepository.saveAll(failures);
        }

        if (known.isEmpty()) {
            // 전 채널 미지/무효 → batch 미생성, markSeen 안 함. → 422
            log.warn("수신 거부(known 채널 0) - deviceCode: {}, rejected: {}", deviceCode, rejected.size());
            return BatchIngestResult.noKnownChannels(device, deviceCode, observedAt, receivedAt, rejected);
        }

        // known ≥ 1 → batch + readings 저장.
        MeasurementBatch batch = measurementBatchRepository.save(MeasurementBatch.builder()
                .device(device).observedAt(observedAt).receivedAt(receivedAt)
                .sourceSeq(request.getSourceSeq()).build());

        List<BatchSsePayload.Reading> sseReadings = new ArrayList<>(known.size());
        for (Map.Entry<SensorChannel, Double> entry : known.entrySet()) {
            SensorChannel channel = entry.getKey();
            Double value = entry.getValue();
            sensorReadingRepository.save(SensorReading.builder()
                    .batch(batch).channel(channel).value(value).build());
            sseReadings.add(new BatchSsePayload.Reading(channel.getId(), channel.getCode(), value));
        }

        markSeen(device, receivedAt);

        // 실시간 전송은 커밋 후(SseBroadcastListener). 저장 트랜잭션 안에서 I/O 하지 않는다.
        eventPublisher.publishEvent(new SseBroadcastEvent("sensor-data", device.getId(),
                new BatchSsePayload(batch.getId(), device.getId(), observedAt, receivedAt, sseReadings)));

        // 채널 상태를 채널 Map 로딩과 같은 패턴으로 일괄 로드(채널별 findById round-trip 회피).
        Map<Long, ChannelStatus> statusByChannelId = new HashMap<>();
        for (ChannelStatus status : channelStatusRepository.findAllById(
                known.keySet().stream().map(SensorChannel::getId).toList())) {
            statusByChannelId.put(status.getChannelId(), status);
        }

        // 저장한 reading 마다 채널별 알람 판정.
        for (Map.Entry<SensorChannel, Double> entry : known.entrySet()) {
            evaluateAlarm(device, batch, entry.getKey(), entry.getValue(), receivedAt, statusByChannelId);
        }

        log.info("batch 수신 저장 - deviceCode: {}, saved: {}, rejected: {}",
                deviceCode, known.size(), rejected.size());
        return BatchIngestResult.saved(batch, device, deviceCode, observedAt, receivedAt, known.size(), rejected);
    }

    /**
     * 엣지 트리거 알람 판정(채널 경계). 임계 초과가 '시작'되는 순간에만 Alert 를 만들고,
     * 초과가 이어지는 동안은 억제한다. 재발화 방지 여유 아래로 복귀하면 알람만 해제한다.
     * 알람 상태는 설정(SensorChannel)이 아니라 ChannelStatus(런타임)에 둔다.
     * 상태 Map 은 receive 가 일괄 로드해 넘긴다(없으면 최초 판독이라 여기서 생성).
     */
    private void evaluateAlarm(Device device, MeasurementBatch batch, SensorChannel channel, double value,
                               Instant now, Map<Long, ChannelStatus> statusByChannelId) {
        boolean breach = anomalyDetector.isAnomaly(channel, value);
        ChannelStatus status = statusByChannelId.get(channel.getId());
        if (status == null) {
            status = channelStatusRepository.save(new ChannelStatus(channel));
            statusByChannelId.put(channel.getId(), status);
        }
        Double threshold = channel.getThresholdValue();

        if (breach && !status.isInAlarm()) {
            // 시간 쿨다운: 직전 발화 후 COOLDOWN 이내면 산발 스파이크로 보고 재발화를 억제한다.
            if (status.getLastAlertAt() != null
                    && Duration.between(status.getLastAlertAt(), now).compareTo(COOLDOWN) < 0) {
                log.debug("Alert 재발화 억제(쿨다운) - channel: {}, value: {}", channel.getCode(), value);
                return;
            }
            AlertSeverity severity = severityFor(channel, value);
            Alert alert = Alert.builder()
                    .device(device).channel(channel).batch(batch)
                    .sensorValue(value).thresholdValue(threshold)
                    .message(String.format("[%s/%s] 임계값 초과! 현재값: %.1f, 임계값: %.1f",
                            device.getName(), channel.getCode(), value, threshold))
                    .severity(severity)
                    .build();
            alertRepository.save(alert);
            status.enterAlarm(now);
            log.warn("Alert 발화 - channel: {}, value: {}, severity: {}", channel.getCode(), value, severity);
            eventPublisher.publishEvent(
                    new SseBroadcastEvent("alert", device.getId(), AlertResponse.from(alert)));
        } else if (!breach && status.isInAlarm() && isReleased(channel, value)) {
            // 재발화 방지 여유 아래로 복귀 → 알람 해제(알림 없음).
            status.clearAlarm();
            log.info("Alert 해제 - channel: {}, value: {}", channel.getCode(), value);
        }
        // breach && inAlarm → 억제 / 여유 구간 → 알람 유지
    }

    // 히스테리시스 해제 밴드(데드밴드). 방향별 수식:
    //   ABOVE: 초과가 이상 → 임계*0.97 아래로 내려와야 해제.
    //   BELOW: 미달이 이상 → 임계*1.03 위로 올라와야 해제. (최소 구현, 데모 미사용)
    private boolean isReleased(SensorChannel channel, double value) {
        Double threshold = channel.getThresholdValue();
        if (threshold == null) {
            return true;
        }
        return channel.getThresholdDirection() == ThresholdDirection.BELOW
                ? value > threshold * (2 - RELEASE_RATIO)
                : value < threshold * RELEASE_RATIO;
    }

    // 발화 시점 심각도. 방향별:
    //   ABOVE: 임계*1.1 이하 WARNING, 초과 CRITICAL.
    //   BELOW: 임계*0.9 이상 WARNING, 미만 CRITICAL. (최소 구현, 데모 미사용)
    private AlertSeverity severityFor(SensorChannel channel, double value) {
        Double threshold = channel.getThresholdValue();
        if (threshold == null) {
            return AlertSeverity.CRITICAL;
        }
        boolean warning = channel.getThresholdDirection() == ThresholdDirection.BELOW
                ? value >= threshold * (2 - CRITICAL_RATIO)
                : value <= threshold * CRITICAL_RATIO;
        return warning ? AlertSeverity.WARNING : AlertSeverity.CRITICAL;
    }

    // 수신 하트비트는 Device(설정)가 아니라 DeviceStatus(텔레메트리)에 찍는다 — 설정 감사 오염 방지.
    private void markSeen(Device device, Instant now) {
        DeviceStatus status = deviceStatusRepository.findById(device.getId())
                .orElseGet(() -> deviceStatusRepository.save(new DeviceStatus(device, now)));
        status.markSeen(now);
    }

    @Transactional(readOnly = true)
    public List<ReadingResponse> getReadingsByChannel(String employeeId, Long channelId, int limit) {
        User user = accessControlService.getUser(employeeId);
        SensorChannel channel = accessControlService.getChannel(channelId);
        accessControlService.assertCanAccessChannel(user, channel);
        int capped = Math.min(Math.max(limit, 1), MAX_RECENT);
        return sensorReadingRepository
                .findByChannelIdOrderByObservedAtDesc(channelId, PageRequest.of(0, capped))
                .stream().map(ReadingResponse::from).toList();
    }
}
