package dev.yeon.iotsensorplatform.ax.client;

import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainRequest;
import dev.yeon.iotsensorplatform.ax.dto.AnomalyExplainResponse;
import dev.yeon.iotsensorplatform.ax.dto.FreshnessDiagnoseRequest;
import dev.yeon.iotsensorplatform.ax.dto.FreshnessDiagnoseResponse;

/**
 * AX(Python) 서비스 클라이언트. 이상 근거·권고 생성과 끊김 원인 진단을
 * HTTP 요청-응답으로 위임한다. 수신 hot path 밖(알림 발생·스케줄 트리거)에서만 호출한다.
 */
public interface AxClient {

    AnomalyExplainResponse explainAnomaly(AnomalyExplainRequest request);

    FreshnessDiagnoseResponse diagnoseFreshness(FreshnessDiagnoseRequest request);
}
