package dev.bugi.sensor.ax.client;

import dev.bugi.sensor.ax.config.AxProperties;
import dev.bugi.sensor.ax.dto.AnomalyExplainRequest;
import dev.bugi.sensor.ax.dto.AnomalyExplainResponse;
import dev.bugi.sensor.ax.dto.FreshnessDiagnoseRequest;
import dev.bugi.sensor.ax.dto.FreshnessDiagnoseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * RestClient 기반 AX 클라이언트 구현.
 *
 * explainAnomaly는 AlertEnrichmentScheduler가 Alert 근거 보강에,
 * diagnoseFreshness는 FreshnessScheduler가 침묵 원인진단에 호출한다.
 * 둘 다 수신 hot path 밖(스케줄 트리거)이라 적재 지연에 영향이 없다.
 */
@Slf4j
@Component
public class HttpAxClient implements AxClient {

    private final RestClient restClient;

    public HttpAxClient(AxProperties properties) {
        // FastAPI(uvicorn/h11)는 HTTP/1.1만 지원 — 기본 JDK HttpClient의 HTTP/2 업그레이드(h2c)
        // 시도가 "Invalid HTTP request"로 거부되므로 HTTP/1.1로 고정한다.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // AX request_timeout(30초)보다 약간 크게 잡아 서버 응답 대기 중 조기 절단을 방지한다.
        factory.setReadTimeout(java.time.Duration.ofSeconds(35));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
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
