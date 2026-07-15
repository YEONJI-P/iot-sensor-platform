package dev.yeon.iotsensorplatform.ax.client;

import dev.yeon.iotsensorplatform.ax.config.AxProperties;
import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainRequest;
import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainResponse;
import dev.yeon.iotsensorplatform.ax.dto.FreshnessDiagnoseRequest;
import dev.yeon.iotsensorplatform.ax.dto.FreshnessDiagnoseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * RestClient 기반 AX 클라이언트 구현.
 *
 * 골격: 아직 수신·알림 흐름에 연결하지 않았다. 빈으로 주입 가능한 상태로 두고,
 * 실제 호출(Alert의 evidence/recommendation 채우기 등)은 다음 단계에서 연결한다.
 */
@Slf4j
@Component
public class HttpAxClient implements AxClient {

    private final RestClient restClient;

    public HttpAxClient(AxProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Override
    public AnomalyExplainResponse explainAnomaly(AnomalyExplainRequest request) {
        return restClient.post()
                .uri("/ax/explain-anomaly")
                .body(request)
                .retrieve()
                .body(AnomalyExplainResponse.class);
    }

    @Override
    public FreshnessDiagnoseResponse diagnoseFreshness(FreshnessDiagnoseRequest request) {
        return restClient.post()
                .uri("/ax/diagnose-freshness")
                .body(request)
                .retrieve()
                .body(FreshnessDiagnoseResponse.class);
    }
}
