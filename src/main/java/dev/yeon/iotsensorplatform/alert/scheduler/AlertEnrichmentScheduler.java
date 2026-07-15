package dev.yeon.iotsensorplatform.alert.scheduler;

import dev.yeon.iotsensorplatform.alert.entity.Alert;
import dev.yeon.iotsensorplatform.alert.repository.AlertRepository;
import dev.yeon.iotsensorplatform.ax.client.AxClient;
import dev.yeon.iotsensorplatform.ax.config.AxProperties;
import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainRequest;
import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainResponse;
import dev.yeon.iotsensorplatform.device.entity.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * evidence가 비어 있는 Alert를 주기적으로 AX 서비스로 보강한다.
 * 수신 hot path 밖(스케줄 트리거)에서만 AX를 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEnrichmentScheduler {

    private final AlertRepository alertRepository;
    private final AxClient axClient;
    private final AxProperties axProperties;

    @Scheduled(fixedRateString = "30000")
    @Transactional
    public void enrichAlerts() {
        if (!axProperties.isEnabled()) {
            return;
        }

        List<Alert> alerts = alertRepository.findTop20ByEvidenceIsNullOrderByCreatedAtDesc();
        for (Alert alert : alerts) {
            // 임계값 alert엔 항상 sensorValue가 있으나, 없으면 double 언박싱 NPE를 피해 건너뛴다.
            if (alert.getSensorValue() == null) {
                continue;
            }

            Device device = alert.getDevice();
            AnomalyExplainRequest request = new AnomalyExplainRequest(
                    device.getName(),
                    device.getType() != null ? device.getType().name() : null,
                    alert.getSensorValue(),
                    alert.getThresholdValue(),
                    alert.getMessage(),
                    null
            );

            try {
                AnomalyExplainResponse response = axClient.explainAnomaly(request);
                alert.enrich(response.evidence(), response.recommendation());
            } catch (Exception e) {
                // AX가 다운돼도 스케줄러가 죽지 않도록 개별 alert 실패는 무시하고 계속 진행한다.
                log.warn("AX 알림 보강 실패 (alertId={}): {}", alert.getId(), e.getMessage());
            }
        }
    }
}
