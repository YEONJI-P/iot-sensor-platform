"""Gemini provider — Google Generative AI SDK 직접 호출.

google 패키지는 지연 import 한다. echo만 쓰는 환경(테스트 포함)에서는
이 모듈이 로드되지 않으므로 해당 패키지가 없어도 무방하다.

주의(스켈레톤): 실제 사용 가능한 모델명·SDK 버전은 Google AI Studio에서
최신 기준으로 확인 후 pyproject/.env를 맞춘다.
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
        self._model = None  # 첫 호출 시 지연 생성

    def _ensure_model(self):
        if self._model is None:
            import google.generativeai as genai  # 지연 import

            genai.configure(api_key=self._api_key)
            self._model = genai.GenerativeModel(self._model_name)
        return self._model

    def generate(self, prompt: str) -> str:
        model = self._ensure_model()
        response = None
        try:
            response = model.generate_content(
                prompt,
                request_options={"timeout": self._timeout},
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
