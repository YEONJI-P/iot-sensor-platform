"""데이터 끊김 원인 진단 /explain/freshness 라우트 (spine② 데이터 안 옴).

freshness 신호(기대 주기 초과)와 실패 적재 유무를 규칙으로 종합해 1차 원인을
가른 뒤, 사람이 읽는 원인 리포트를 LLM으로 생성한다. 규격변경 의심 vs 소스 침묵.
"""

from fastapi import APIRouter, Depends

from ..dependencies import get_provider
from ..providers.base import LLMProvider
from ..schemas import FreshnessDiagnoseRequest, FreshnessDiagnoseResponse

router = APIRouter(prefix="/explain", tags=["freshness"])


def _rule_cause(req: FreshnessDiagnoseRequest) -> str:
    """규칙 기반 1차 원인 분류.

    최근 실패 적재가 있으면 형식 불일치(규격변경) 쪽, 없으면 소스 침묵 쪽으로 본다.
    """
    if req.failed_reading_recent_count and req.failed_reading_recent_count > 0:
        return "규격변경 의심 (최근 실패 적재 발생)"
    return "소스 침묵 의심 (수신 자체가 끊김)"


def _build_prompt(req: FreshnessDiagnoseRequest, cause: str) -> str:
    lines = [
        "너는 제조 설비 모니터링 보조자다. 아래 데이터 수신 끊김의 원인과 점검 순서를 한국어로 3~4문장으로 설명하라.",
        f"- 장치: {req.device_name}",
        f"- 기대 수신 주기(초): {req.expected_interval_seconds}",
        f"- 1차 추정 원인: {cause}",
    ]
    if req.last_seen_at:
        lines.append(f"- 마지막 수신: {req.last_seen_at}")
    if req.elapsed_seconds is not None:
        lines.append(f"- 경과(초): {req.elapsed_seconds}")
    if req.failed_reading_recent_count is not None:
        lines.append(f"- 최근 실패 적재 건수: {req.failed_reading_recent_count}")
    return "\n".join(lines)


@router.post("/freshness", response_model=FreshnessDiagnoseResponse)
def diagnose_freshness(
    req: FreshnessDiagnoseRequest,
    provider: LLMProvider = Depends(get_provider),
) -> FreshnessDiagnoseResponse:
    """데이터 끊김 신호에 원인 추정과 리포트를 붙여 반환한다."""
    cause = _rule_cause(req)
    report = provider.generate(_build_prompt(req, cause))
    return FreshnessDiagnoseResponse(
        cause=cause,
        report=report,
        model=provider.model_name,
    )
