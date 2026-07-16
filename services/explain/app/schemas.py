"""요청·응답 Pydantic v2 스키마.

Spring이 camelCase JSON을 보내므로, 필드는 snake_case로 두되
alias_generator=to_camel + populate_by_name 으로 camelCase를 함께 받는다.
(예: Python device_name  <->  JSON deviceName)
"""

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """camelCase JSON과 snake_case 파이썬 필드를 함께 지원하는 베이스."""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
    )


# ── spine① 이상 근거·권고 ─────────────────────────────────────────────

class AnomalyExplainRequest(CamelModel):
    """이상 알림에 대한 근거·권고 생성 요청.

    탐지는 Spring이 규칙으로 끝냈고, 여기에는 판정에 쓰인 신호가 담겨 온다.
    """

    device_name: str = Field(..., description="장치 이름")
    sensor_type: str | None = Field(None, description="센서 타입(TEMPERATURE 등)")
    unit: str | None = Field(None, description="측정 단위(예: °C, kPa, A)")
    value: float = Field(..., description="이상으로 판정된 측정값")
    threshold: float | None = Field(None, description="임계값")
    message: str | None = Field(None, description="Spring이 만든 원본 알림 메시지")
    recent_values: list[float] | None = Field(None, description="직전 측정값들(추세 근거)")
    # Spring이 최근 윈도우에서 규칙으로 계산한 파생 지표. 여기선 서술만 하고 재계산하지 않는다.
    breach_rate: float | None = Field(None, description="윈도우 내 임계 초과 비율(0~1)")
    trend: float | None = Field(None, description="추세(후반 평균 - 전반 평균), 양수면 상승 중")
    volatility: float | None = Field(None, description="변동성(표준편차)")


class AnomalyExplainResponse(CamelModel):
    """이상 근거·권고 응답. Spring Alert의 evidence/recommendation에 채워진다."""

    evidence: str = Field(..., description="이상 판정 근거")
    recommendation: str = Field(..., description="권장 조치")
    severity: str = Field(..., description="심각도(INFO/WARNING/CRITICAL)")
    model: str = Field(..., description="사용된 provider/모델 식별자")


# ── spine② 데이터 끊김 원인 진단 ─────────────────────────────────────

class FreshnessDiagnoseRequest(CamelModel):
    """데이터 수신 끊김에 대한 원인 진단 요청."""

    device_name: str = Field(..., description="장치 이름")
    expected_interval_seconds: int = Field(..., description="기대 수신 주기(초)")
    last_seen_at: str | None = Field(None, description="마지막 수신 시각(ISO-8601)")
    elapsed_seconds: int | None = Field(None, description="마지막 수신 후 경과 초")
    failed_reading_recent_count: int | None = Field(
        None, description="최근 실패 적재 건수(규격변경/장치오류 신호)"
    )


class FreshnessDiagnoseResponse(CamelModel):
    """원인 진단 응답."""

    cause: str = Field(..., description="추정 원인(예: 규격변경 의심 vs 소스 침묵)")
    report: str = Field(..., description="사람이 읽는 원인 진단 리포트")
    model: str = Field(..., description="사용된 provider/모델 식별자")
