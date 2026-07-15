"""Echo provider — 키 없이 동작하는 스텁.

실제 LLM 호출 없이 프롬프트 요약을 돌려주므로, 인증·비용 없이 골격 전체를
end-to-end로 구동·테스트할 수 있다. Gemini 연동 전 기본 provider.
"""

from .base import LLMProvider


class EchoProvider(LLMProvider):
    """프롬프트를 그대로 반향하는 개발용 provider."""

    def generate(self, prompt: str) -> str:
        preview = prompt.strip().replace("\n", " ")
        if len(preview) > 200:
            preview = preview[:200] + "..."
        return f"[echo provider — 실제 LLM 미연동] 입력 프롬프트 요약: {preview}"

    @property
    def model_name(self) -> str:
        return "echo-0"
