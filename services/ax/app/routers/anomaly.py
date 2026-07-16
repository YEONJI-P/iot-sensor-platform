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


def _metrics_phrase(req: AnomalyExplainRequest) -> str:
    """Spring이 계산한 윈도우 지표를 사람이 읽는 한 구절로 정리한다(값 재계산 없음)."""
    parts: list[str] = []
    if req.breach_rate is not None:
        parts.append(f"최근 초과율 {round(req.breach_rate * 100)}%")
    if req.trend is not None:
        if req.trend > 0:
            parts.append(f"상승 추세(+{req.trend:.1f})")
        elif req.trend < 0:
            parts.append(f"하강 추세({req.trend:.1f})")
        else:
            parts.append("추세 평탄")
    if req.volatility is not None:
        parts.append(f"변동성 σ={req.volatility:.1f}")
    return ", ".join(parts)


def _build_prompt(req: AnomalyExplainRequest) -> str:
    """규칙으로 모은 신호를 LLM 프롬프트로 조립한다."""
    lines = [
        "너는 제조 설비 모니터링 보조자다. 아래 센서 이상에 대한 권장 조치를 한국어로 2~3문장으로 제시하라.",
        f"- 장치: {req.device_name}",
    ]
    if req.sensor_type:
        lines.append(f"- 센서 타입: {req.sensor_type}")
    unit = f" {req.unit}" if req.unit else ""
    lines.append(f"- 측정값: {req.value}{unit}")
    if req.threshold is not None:
        lines.append(f"- 임계값: {req.threshold}{unit}")
    if req.recent_values:
        lines.append(f"- 직전 값들: {req.recent_values}")
    metrics = _metrics_phrase(req)
    if metrics:
        lines.append(f"- 최근 추세 지표: {metrics}")
        lines.append(
            "  (초과율이 낮고 추세가 평탄하면 간헐 스파이크로 노이즈 가능성, "
            "초과율이 높거나 상승 추세면 지속 이상으로 판단해 권고 수위를 높여라.)"
        )
    return "\n".join(lines)


@router.post("/explain-anomaly", response_model=AnomalyExplainResponse)
def explain_anomaly(
    req: AnomalyExplainRequest,
    provider: LLMProvider = Depends(get_provider),
) -> AnomalyExplainResponse:
    """이상 신호에 근거와 권고를 붙여 반환한다."""
    # 근거는 규칙으로 조립 (LLM 없이 결정적)
    unit = f"{req.unit}" if req.unit else ""
    evidence = f"{req.device_name}"
    if req.sensor_type:
        evidence += f" {req.sensor_type}"
    evidence += f" 측정값 {req.value}{unit}"
    if req.threshold is not None:
        evidence += f"가 임계값 {req.threshold}{unit}를 초과"
    metrics = _metrics_phrase(req)
    if metrics:
        evidence += f" ({metrics})"

    # 권고는 LLM으로 생성
    recommendation = provider.generate(_build_prompt(req))

    return AnomalyExplainResponse(
        evidence=evidence,
        recommendation=recommendation,
        severity="WARNING",
        model=provider.model_name,
    )
