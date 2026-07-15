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
            raise ValueError("GEMINI_API_KEY가 필요합니다 (AX_PROVIDER=gemini).")
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
        response = model.generate_content(
            prompt,
            request_options={"timeout": self._timeout},
        )
        return (response.text or "").strip()

    @property
    def model_name(self) -> str:
        return self._model_name
