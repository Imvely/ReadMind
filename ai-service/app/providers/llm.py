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
from app.providers._gemini import build_client, run_with_retry
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


class GeminiLLM:
    """Google Gemini provider (google-genai SDK 경유).

    client 를 주입하면(.models.generate_content 를 가진 페이크) 네트워크 없이 동작한다.
    벤더 결합은 이 클래스 안에 격리되고, 호출부는 LLMProvider(complete)만 본다.
    client 미주입 시에는 _gemini.build_client 가 google-genai 를 지연 임포트한다.
    """

    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        timeout: float = 60.0,
        max_retries: int = 2,
        use_vertex: bool | None = None,
        project: str = "",
        location: str = "global",
        client: object | None = None,
    ) -> None:
        if not api_key:
            raise ProviderConfigError("LLM_API_KEY 가 비어 있다(Gemini).")
        if not model:
            raise ProviderConfigError("LLM_MODEL 이 비어 있다(Gemini).")
        self._model = model
        self._max_retries = max_retries
        self._client = client or build_client(
            api_key=api_key,
            timeout=timeout,
            use_vertex=use_vertex,
            project=project,
            location=location,
        )

    def complete(self, system: str, user: str, *, json_mode: bool = False) -> str:
        # config 는 dict 로 전달(google.genai.types 임포트 불필요 — SDK 가 coerce).
        config: dict[str, object] = {"system_instruction": system}
        if json_mode:
            config["response_mime_type"] = "application/json"

        resp = run_with_retry(
            lambda: self._client.models.generate_content(  # type: ignore[attr-defined]
                model=self._model, contents=user, config=config
            ),
            self._max_retries,
        )
        text = getattr(resp, "text", None)
        if isinstance(text, str) and text:
            return text
        # 안전 필터·빈 응답 등: 근거를 담아 명시적으로 실패시킨다(에러 삼키지 않음).
        feedback = getattr(resp, "prompt_feedback", None)
        raise ProviderError(f"Gemini 응답에 텍스트가 없다 (feedback={feedback!r})")


def get_llm_provider(settings: Settings | None = None) -> LLMProvider:
    """환경변수로 선택된 LLM provider를 만든다.

    - gemini: google-genai SDK 경유(GeminiLLM).
    - commercial/selfhosted: OpenAI 호환 구현체(엔드포인트만 다름).
    벤더별 분기는 여기서만 갈라낸다 — 호출부는 LLMProvider(complete)만 본다.
    """
    cfg = settings or get_settings()
    if cfg.llm_provider == "gemini":
        return GeminiLLM(
            api_key=cfg.llm_api_key,
            model=cfg.llm_model,
            timeout=cfg.llm_timeout_seconds,
            max_retries=cfg.llm_max_retries,
            use_vertex=cfg.gemini_vertexai,
            project=cfg.gemini_project,
            location=cfg.gemini_location,
        )
    return OpenAICompatLLM(
        api_base=cfg.llm_api_base,
        model=cfg.llm_model,
        api_key=cfg.llm_api_key,
        timeout=cfg.llm_timeout_seconds,
        max_retries=cfg.llm_max_retries,
    )
