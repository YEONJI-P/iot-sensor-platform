package dev.yeon.iotsensorplatform.ax.client;

import dev.yeon.iotsensorplatform.ax.config.AxProperties;
import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainRequest;
import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainResponse;
import dev.yeon.iotsensorplatform.ax.dto.FreshnessDiagnoseRequest;
import dev.yeon.iotsensorplatform.ax.dto.FreshnessDiagnoseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

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
