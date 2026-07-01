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


# Gemini 재시도 대상 상태 코드(일시적 오류). 그 외는 즉시 올린다.
_GEMINI_TRANSIENT_CODES = frozenset({429, 500, 502, 503, 504})


def _resolve_vertex(use_vertex: bool | None, api_key: str) -> bool:
    """Vertex 사용 여부. 명시값 우선, 없으면 키 접두사로 감지.

    Vertex AI Express 키는 "AQ." 로 시작, Developer API 키는 "AIza" 로 시작한다.
    """
    if use_vertex is not None:
        return use_vertex
    return api_key.startswith("AQ.")


class GeminiLLM:
    """Google Gemini provider (google-genai SDK 경유).

    google-genai 는 지연 임포트한다 — 라이브러리 미설치 환경에서도 이 모듈 임포트가
    깨지지 않게 하고(테스트는 client 주입), Gemini 를 실제 쓸 때만 로드한다.

    client 를 주입하면(.models.generate_content 를 가진 페이크) 네트워크 없이 동작한다.
    벤더 결합은 이 클래스 안에 격리되고, 호출부는 LLMProvider(complete)만 본다.
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

        if client is not None:
            self._client = client
            return

        # 지연 임포트: 실제 Gemini 사용 시에만 google-genai 필요.
        try:
            from google import genai
        except ImportError as exc:  # pragma: no cover - 설치 안내
            raise ProviderConfigError(
                "google-genai 가 설치되어 있지 않다. requirements.txt 참조."
            ) from exc

        # 키 접두사로 Vertex Express 여부 자동 감지(명시 설정이 있으면 우선).
        vertex = _resolve_vertex(use_vertex, api_key)
        http_options = {"timeout": int(timeout * 1000)}  # google-genai 는 ms 단위
        if vertex:
            # Express 모드: 프로젝트/리전은 API 키에 인코딩됨(project/location 불필요).
            kwargs: dict[str, object] = {
                "vertexai": True,
                "api_key": api_key,
                "http_options": http_options,
            }
            if project:  # 비-express Vertex(서비스계정 등)일 때만.
                kwargs.update(project=project, location=location)
                kwargs.pop("api_key")
            self._client = genai.Client(**kwargs)
        else:
            self._client = genai.Client(api_key=api_key, http_options=http_options)

    def complete(self, system: str, user: str, *, json_mode: bool = False) -> str:
        # config 는 dict 로 전달(google.genai.types 임포트 불필요 — SDK 가 coerce).
        config: dict[str, object] = {"system_instruction": system}
        if json_mode:
            config["response_mime_type"] = "application/json"

        last_exc: Exception | None = None
        for attempt in range(self._max_retries + 1):
            try:
                resp = self._client.models.generate_content(  # type: ignore[attr-defined]
                    model=self._model,
                    contents=user,
                    config=config,
                )
            except Exception as exc:  # google.genai.errors.APIError 등
                code = getattr(exc, "code", None) or getattr(exc, "status_code", None)
                transient = code in _GEMINI_TRANSIENT_CODES or _looks_transient(exc)
                if transient and attempt < self._max_retries:
                    last_exc = exc
                    continue
                raise ProviderError(f"Gemini 호출 실패: {exc}") from exc

            text = getattr(resp, "text", None)
            if isinstance(text, str) and text:
                return text
            # 안전 필터·빈 응답 등: 근거를 담아 명시적으로 실패시킨다(에러 삼키지 않음).
            feedback = getattr(resp, "prompt_feedback", None)
            raise ProviderError(f"Gemini 응답에 텍스트가 없다 (feedback={feedback!r})")

        # 도달 불가(루프에서 반환/예외) — 방어적.
        raise ProviderError(f"Gemini 호출 실패(재시도 소진): {last_exc}")


def _looks_transient(exc: Exception) -> bool:
    """상태 코드가 없을 때 연결/타임아웃류 예외를 이름으로 판별."""
    name = type(exc).__name__.lower()
    return any(k in name for k in ("timeout", "connect", "unavailable"))


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
