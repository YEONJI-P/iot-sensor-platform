"""의존성 주입 — Settings와 LLM provider.

요청마다 provider를 새로 만들지 않도록 @lru_cache로 캐시된 팩토리를
Depends로 노출한다. provider는 얇은 인터페이스라 Gemini/OpenAI/Claude로
교체 가능하며, 기본값은 키 없이 동작하는 echo 스텁이다.
"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

from .providers.base import LLMProvider
from .providers.echo import EchoProvider


class Settings(BaseSettings):
    """환경변수 기반 설정. .env 가 있으면 자동 로드한다."""

    explain_provider: str = "echo"     # echo | gemini
    gemini_api_key: str = ""           # EXPLAIN_PROVIDER=gemini 일 때만 필요
    model_name: str = "gemini-2.0-flash"
    port: int = 23200
    request_timeout: float = 30.0

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    """Settings 싱글톤. 첫 호출 시 1회만 생성된다."""
    return Settings()


@lru_cache
def get_provider() -> LLMProvider:
    """LLM provider 싱글톤.

    EXPLAIN_PROVIDER 값에 따라 구현체를 고른다. gemini 구현체는 google 패키지를
    지연 import 하므로, echo만 쓰는 환경에서는 해당 패키지가 없어도 된다.
    """
    settings = get_settings()
    if settings.explain_provider == "gemini":
        from .providers.gemini import GeminiProvider

        return GeminiProvider(
            api_key=settings.gemini_api_key,
            model_name=settings.model_name,
            timeout=settings.request_timeout,
        )
    return EchoProvider()
