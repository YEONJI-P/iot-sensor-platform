"""이상 근거·권고 생성 /ax/explain-anomaly 라우트 (spine① 이상).

흐름: Spring이 규칙으로 이상을 판정해 신호를 보내면,
  1) 신호를 사람이 읽는 근거 문장으로 정리(규칙)
  2) 권장 조치는 LLM으로 생성
탐지는 규칙, 설명·권고만 LLM이라는 경계를 지킨다.
"""

from fastapi import APIRouter, Depends

from ..dependencies import get_provider
from ..providers.base import LLMProvider
from ..schemas import AnomalyExplainRequest, AnomalyExplainResponse

router = APIRouter(prefix="/ax", tags=["anomaly"])


def _build_prompt(req: AnomalyExplainRequest) -> str:
    """규칙으로 모은 신호를 LLM 프롬프트로 조립한다."""
    lines = [
        "너는 제조 설비 모니터링 보조자다. 아래 센서 이상에 대한 권장 조치를 한국어로 2~3문장으로 제시하라.",
        f"- 장치: {req.device_name}",
    ]
    if req.sensor_type:
        lines.append(f"- 센서 타입: {req.sensor_type}")
    lines.append(f"- 측정값: {req.value}")
    if req.threshold is not None:
        lines.append(f"- 임계값: {req.threshold}")
    if req.recent_values:
        lines.append(f"- 직전 값들: {req.recent_values}")
    return "\n".join(lines)


@router.post("/explain-anomaly", response_model=AnomalyExplainResponse)
def explain_anomaly(
    req: AnomalyExplainRequest,
    provider: LLMProvider = Depends(get_provider),
) -> AnomalyExplainResponse:
    """이상 신호에 근거와 권고를 붙여 반환한다."""
    # 근거는 규칙으로 조립 (LLM 없이 결정적)
    evidence = f"{req.device_name}"
    if req.sensor_type:
        evidence += f" {req.sensor_type}"
    evidence += f" 측정값 {req.value}"
    if req.threshold is not None:
        evidence += f"가 임계값 {req.threshold}를 초과"

    # 권고는 LLM으로 생성
    recommendation = provider.generate(_build_prompt(req))

    return AnomalyExplainResponse(
        evidence=evidence,
        recommendation=recommendation,
        severity="WARNING",
        model=provider.model_name,
    )
