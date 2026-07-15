"""LLM provider 추상 인터페이스.

벤더 비종속 설계 — 라우터/서비스는 이 인터페이스에만 의존하고,
구현체(echo, gemini, ...)는 config로 교체된다. 프레임워크(CrewAI/LangGraph)를
쓰지 않고 SDK를 직접 감싸 규모에 맞게 유지한다.
"""

from abc import ABC, abstractmethod


class LLMProvider(ABC):
    """단일 프롬프트를 받아 텍스트를 생성하는 최소 인터페이스."""

    @abstractmethod
    def generate(self, prompt: str) -> str:
        """프롬프트에 대한 응답 텍스트를 반환한다."""
        raise NotImplementedError

    @property
    @abstractmethod
    def model_name(self) -> str:
        """응답에 기록할 provider/모델 식별자."""
        raise NotImplementedError
