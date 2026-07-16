"""Gemini provider — Google Gen AI SDK(google-genai) 직접 호출.

google 패키지는 지연 import 한다. echo만 쓰는 환경(테스트 포함)에서는
이 모듈이 로드되지 않으므로 해당 패키지가 없어도 무방하다.

구 google-generativeai(지원 종료) → 신규 google-genai 로 이전.
실제 사용 가능한 모델명은 Google AI Studio에서 최신 기준으로 확인 후 .env(MODEL_NAME)를 맞춘다.
"""

from .base import LLMProvider


class GeminiProvider(LLMProvider):
    """Gemini 무료 티어를 감싸는 provider."""

    def __init__(self, api_key: str, model_name: str, timeout: float = 30.0) -> None:
        if not api_key:
            raise ValueError("GEMINI_API_KEY가 필요합니다 (EXPLAIN_PROVIDER=gemini).")
        self._model_name = model_name
        self._timeout = timeout
        self._api_key = api_key
        self._client = None  # 첫 호출 시 지연 생성

    def _ensure_client(self):
        if self._client is None:
            from google import genai  # 지연 import (google-genai)

            # 타임아웃은 ms 단위 HttpOptions 로 전달
            self._client = genai.Client(
                api_key=self._api_key,
                http_options={"timeout": int(self._timeout * 1000)},
            )
        return self._client

    def generate(self, prompt: str) -> str:
        client = self._ensure_client()
        response = None
        try:
            response = client.models.generate_content(
                model=self._model_name,
                contents=prompt,
            )
            return (response.text or "").strip()
        except Exception as exc:  # noqa: BLE001 — 응답 없음/차단 시 폴백 반환
            reason = self._extract_reason(response, exc) if response is not None else str(exc)
            return f"[Gemini 응답 없음: {reason}]"

    @staticmethod
    def _extract_reason(response, exc: Exception) -> str:
        """차단/후보 없음 등의 사유를 사람이 읽을 수 있는 문자열로 추출한다."""
        try:
            feedback = getattr(response, "prompt_feedback", None)
            block_reason = getattr(feedback, "block_reason", None)
            if block_reason:
                return f"prompt 차단({block_reason})"
            candidates = getattr(response, "candidates", None)
            if not candidates:
                return "후보 없음"
            finish_reason = getattr(candidates[0], "finish_reason", None)
            if finish_reason:
                return f"finish_reason={finish_reason}"
        except Exception:  # noqa: BLE001 — 사유 추출 실패 시 원 예외로 대체
            pass
        return str(exc)

    @property
    def model_name(self) -> str:
        return self._model_name
