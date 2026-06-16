"""LLMProvider 추상화 + OpenAI 호환 구현체 (명세서 §5.7).

    class LLMProvider(Protocol):
        def complete(self, system: str, user: str, *, json_mode: bool=False) -> str: ...

구현체는 OpenAI Chat Completions 호환 엔드포인트({api_base}/chat/completions)를
호출한다. 상용 게이트웨이와 자체 호스팅 vLLM 모두 이 규격을 따르므로,
.env(LLM_API_BASE/LLM_MODEL) 변경만으로 교체된다 — 벤더 직접 결합 없음.
"""

from __future__ import annotations

from typing import Protocol, runtime_checkable

import httpx

from app.core.config import Settings, get_settings
from app.providers._http import post_json
from app.providers.errors import ProviderConfigError, ProviderError


@runtime_checkable
class LLMProvider(Protocol):
    """텍스트 생성 provider 계약."""

    def complete(self, system: str, user: str, *, json_mode: bool = False) -> str: ...


class OpenAICompatLLM:
    """OpenAI Chat Completions 호환 LLM provider.

    client를 주입하면(테스트의 httpx.MockTransport 등) 실제 네트워크 없이 동작한다.
    """

    def __init__(
        self,
        *,
        api_base: str,
        model: str,
        api_key: str = "",
        timeout: float = 60.0,
        max_retries: int = 2,
        client: httpx.Client | None = None,
    ) -> None:
        if not api_base:
            raise ProviderConfigError("LLM_API_BASE 가 비어 있다.")
        if not model:
            raise ProviderConfigError("LLM_MODEL 이 비어 있다.")
        self._model = model
        self._api_key = api_key
        self._max_retries = max_retries
        self._owns_client = client is None
        self._client = client or httpx.Client(
            base_url=api_base.rstrip("/"), timeout=timeout
        )

    def complete(self, system: str, user: str, *, json_mode: bool = False) -> str:
        payload: dict[str, object] = {
            "model": self._model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        if json_mode:
            # OpenAI 호환 JSON 강제 모드. 지원하지 않는 백엔드는 무시한다.
            payload["response_format"] = {"type": "json_object"}

        data = post_json(
            self._client,
            "/chat/completions",
            payload,
            api_key=self._api_key,
            max_retries=self._max_retries,
        )
        try:
            content = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise ProviderError(f"예상치 못한 응답 형식: {data!r}") from exc
        if not isinstance(content, str):
            raise ProviderError(f"content 가 문자열이 아니다: {content!r}")
        return content

    def close(self) -> None:
        if self._owns_client:
            self._client.close()


def get_llm_provider(settings: Settings | None = None) -> LLMProvider:
    """환경변수로 선택된 LLM provider를 만든다.

    commercial/selfhosted 모두 OpenAI 호환 구현체를 쓴다(엔드포인트만 다름).
    벤더별 분기가 필요해지면 여기서만 갈라낸다 — 호출부는 LLMProvider만 본다.
    """
    cfg = settings or get_settings()
    return OpenAICompatLLM(
        api_base=cfg.llm_api_base,
        model=cfg.llm_model,
        api_key=cfg.llm_api_key,
        timeout=cfg.llm_timeout_seconds,
        max_retries=cfg.llm_max_retries,
    )
