package dev.bugi.sensor.explain.client;

import dev.bugi.sensor.explain.dto.AnomalyExplainRequest;
import dev.bugi.sensor.explain.dto.AnomalyExplainResponse;
import dev.bugi.sensor.explain.dto.FreshnessDiagnoseRequest;
import dev.bugi.sensor.explain.dto.FreshnessDiagnoseResponse;

/**
 * explain(Python) 서비스 클라이언트. 이상 근거·권고 생성과 끊김 원인 진단을
 * HTTP 요청-응답으로 위임한다. 수신 hot path 밖(알림 발생·스케줄 트리거)에서만 호출한다.
 */
public interface ExplainClient {

    AnomalyExplainResponse explainAnomaly(AnomalyExplainRequest request);

    FreshnessDiagnoseResponse diagnoseFreshness(FreshnessDiagnoseRequest request);
}
