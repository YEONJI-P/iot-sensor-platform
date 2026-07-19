package dev.bugi.sensor.alert.scheduler;

import dev.bugi.sensor.alert.dto.EnrichTarget;
import dev.bugi.sensor.alert.repository.AlertRepository;
import dev.bugi.sensor.explain.client.ExplainClient;
import dev.bugi.sensor.explain.config.ExplainProperties;
import dev.bugi.sensor.explain.dto.AnomalyExplainRequest;
import dev.bugi.sensor.explain.dto.AnomalyExplainResponse;
import dev.bugi.sensor.device.entity.SensorChannel;
import dev.bugi.sensor.sensordata.entity.SensorReading;
import dev.bugi.sensor.sensordata.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * evidence가 비어 있는 임계 Alert를 주기적으로 explain 서비스로 보강한다.
 * 수신 hot path 밖(스케줄 트리거)에서만 explain을 호출한다.
 *
 * 외부 HTTP(explain) 호출은 트랜잭션 밖에서 수행한다. 트랜잭션을 열어둔 채로
 * 최대 20회 순차 HTTP 호출을 하면 DB 커넥션을 장기 점유하기 때문이다.
 * 읽기는 프로젝션(EnrichTarget)으로 즉시 값만 확보하고, 쓰기는 alert별로
 * findById/save 각각의 짧은 트랜잭션에서 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEnrichmentScheduler {

    // 설명용 윈도우 크기(최근 판독 건수). 탐지가 아니라 근거·권고를 만드는 데만 쓴다.
    private static final int WINDOW = 20;
    // 지표를 신뢰할 최소 표본. 이보다 적으면(신규 채널 등) 지표 없이 단건 근거로 보강한다.
    private static final int MIN_SAMPLES = 5;

    private final AlertRepository alertRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final ExplainClient explainClient;
    private final ExplainProperties explainProperties;

    @Scheduled(fixedRateString = "30000")
    public void enrichAlerts() {
        if (!explainProperties.isEnabled()) {
            return;
        }

        List<EnrichTarget> targets = alertRepository.findEnrichTargets(PageRequest.of(0, 20));
        for (EnrichTarget target : targets) {
            // 임계값 alert엔 항상 sensorValue가 있으나, 없으면 double 언박싱 NPE를 피해 건너뛴다.
            if (target.sensorValue() == null) {
                continue;
            }

            // 설명용 윈도우: 채널의 최근 판독을 읽어 규칙으로 추세·이탈률·변동성을 계산(탐지엔 안 씀).
            List<Double> recentValues = recentValues(target.channelId());
            WindowMetrics metrics = WindowMetrics.of(
                    recentValues, target.thresholdValue(), target.thresholdDirection());

            AnomalyExplainRequest request = new AnomalyExplainRequest(
                    target.deviceName(),
                    target.quantityKind(),
                    target.unit(),
                    target.sensorValue(),
                    target.thresholdValue(),
                    target.thresholdDirection(),
                    target.message(),
                    recentValues.isEmpty() ? null : recentValues,
                    metrics.breachRate(),
                    metrics.trend(),
                    metrics.volatility()
            );

            AnomalyExplainResponse response;
            try {
                // 트랜잭션 밖에서 외부 HTTP 호출.
                response = explainClient.explainAnomaly(request);
            } catch (Exception e) {
                // explain이 다운돼도 스케줄러가 죽지 않도록 개별 alert 실패는 무시하고 계속 진행한다.
                log.warn("explain 알림 보강 실패 (alertId={}): {}", target.alertId(), e.getMessage());
                continue;
            }

            // 결과 반영: 짧은 자체 트랜잭션(findById/save)에서 처리.
            alertRepository.findById(target.alertId()).ifPresent(alert -> {
                alert.enrich(response.evidence(), response.recommendation());
                alertRepository.save(alert);
            });
        }
    }

    /** 채널의 최근 판독값을 시간순(과거→현재)으로 반환. 추세 계산이 쉬우라고 뒤집는다. */
    private List<Double> recentValues(Long channelId) {
        // channelId 는 findEnrichTargets 가 'a.channel is not null' 로 걸러 항상 non-null 이라 방어 체크를 두지 않는다.
        List<SensorReading> recent = sensorReadingRepository
                .findByChannelIdOrderByObservedAtDesc(channelId, PageRequest.of(0, WINDOW));
        List<Double> values = new ArrayList<>(recent.size());
        for (int i = recent.size() - 1; i >= 0; i--) {
            values.add(recent.get(i).getValue());
        }
        return values;
    }

    /**
     * 윈도우에서 규칙으로 뽑는 파생 지표. 산발 스파이크 / 점진 열화 / 급성 이상을 가르는 신호다.
     * 표본이 {@link #MIN_SAMPLES} 미만이면 신뢰할 수 없어 모두 null(설명은 단건 근거로 fallback).
     */
    private record WindowMetrics(Double breachRate, Double trend, Double volatility) {

        private static final WindowMetrics EMPTY = new WindowMetrics(null, null, null);

        static WindowMetrics of(List<Double> values, Double threshold,
                                SensorChannel.ThresholdDirection direction) {
            if (values == null || values.size() < MIN_SAMPLES) {
                return EMPTY;
            }
            int n = values.size();

            // 이탈률: 채널 방향에 맞게 임계를 벗어난 판독 비율(threshold 없으면 계산 불가).
            Double breachRate = null;
            if (threshold != null) {
                SensorChannel.ThresholdDirection effectiveDirection = direction == null
                        ? SensorChannel.ThresholdDirection.ABOVE : direction;
                long breaches = values.stream().filter(value -> switch (effectiveDirection) {
                    case ABOVE -> value > threshold;
                    case BELOW -> value < threshold;
                    case ABS_ABOVE -> Math.abs(value) > threshold;
                }).count();
                breachRate = (double) breaches / n;
            }

            // 추세: 후반 절반 평균 - 전반 절반 평균(양수면 상승 중).
            int half = n / 2;
            double front = average(values.subList(0, half));
            double back = average(values.subList(n - half, n));
            double trend = back - front;

            // 변동성: 모표준편차.
            double mean = average(values);
            double var = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / n;
            double volatility = Math.sqrt(var);

            return new WindowMetrics(breachRate, trend, volatility);
        }

        private static double average(List<Double> xs) {
            return xs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
    }
}
