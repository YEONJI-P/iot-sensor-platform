package dev.yeon.iotsensorplatform.alert.scheduler;

import dev.yeon.iotsensorplatform.alert.dto.EnrichTarget;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.ax.client.AxClient;
import dev.yeon.iotsensorplatform.ax.config.AxProperties;
import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainRequest;
import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * evidence가 비어 있는 Alert를 주기적으로 AX 서비스로 보강한다.
 * 수신 hot path 밖(스케줄 트리거)에서만 AX를 호출한다.
 *
 * 외부 HTTP(AX) 호출은 트랜잭션 밖에서 수행한다. 트랜잭션을 열어둔 채로
 * 최대 20회 순차 HTTP 호출을 하면 DB 커넥션을 장기 점유하기 때문이다.
 * 읽기는 프로젝션(EnrichTarget)으로 즉시 값만 확보하고, 쓰기는 alert별로
 * findById/save 각각의 짧은 트랜잭션에서 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEnrichmentScheduler {

    private final AlertRepository alertRepository;
    private final AxClient axClient;
    private final AxProperties axProperties;

    @Scheduled(fixedRateString = "30000")
    public void enrichAlerts() {
        if (!axProperties.isEnabled()) {
            return;
        }

        List<EnrichTarget> targets = alertRepository.findEnrichTargets(PageRequest.of(0, 20));
        for (EnrichTarget target : targets) {
            // 임계값 alert엔 항상 sensorValue가 있으나, 없으면 double 언박싱 NPE를 피해 건너뛴다.
            if (target.sensorValue() == null) {
                continue;
            }

            AnomalyExplainRequest request = new AnomalyExplainRequest(
                    target.deviceName(),
                    target.sensorType() != null ? target.sensorType().name() : null,
                    target.sensorValue(),
                    target.thresholdValue(),
                    target.message(),
                    null
            );

            AnomalyExplainResponse response;
            try {
                // 트랜잭션 밖에서 외부 HTTP 호출.
                response = axClient.explainAnomaly(request);
            } catch (Exception e) {
                // AX가 다운돼도 스케줄러가 죽지 않도록 개별 alert 실패는 무시하고 계속 진행한다.
                log.warn("AX 알림 보강 실패 (alertId={}): {}", target.alertId(), e.getMessage());
                continue;
            }

            // 결과 반영: 짧은 자체 트랜잭션(findById/save)에서 처리.
            alertRepository.findById(target.alertId()).ifPresent(alert -> {
                alert.enrich(response.evidence(), response.recommendation());
                alertRepository.save(alert);
            });
        }
    }
}
