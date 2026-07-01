"""Gemini(google-genai) 공용 헬퍼 — llm.py/embedding.py가 공유.

client 구성·재시도·Vertex 감지를 한곳에 모아 두 provider(GeminiLLM/GeminiEmbedding)가
같은 규칙을 쓰게 한다. google-genai는 지연 임포트(미설치·테스트 환경 보호).
"""

from __future__ import annotations

from collections.abc import Callable
from typing import TypeVar

from app.providers.errors import ProviderConfigError, ProviderError

# 재시도 대상 상태 코드(일시적 오류). 그 외는 즉시 올린다.
TRANSIENT_CODES = frozenset({429, 500, 502, 503, 504})

T = TypeVar("T")


def resolve_vertex(use_vertex: bool | None, api_key: str) -> bool:
    """Vertex 사용 여부. 명시값 우선, 없으면 키 접두사로 감지.

    Vertex AI Express 키는 "AQ." 로 시작, Developer API 키는 "AIza" 로 시작한다.
    """
    if use_vertex is not None:
        return use_vertex
    return api_key.startswith("AQ.")


def _looks_transient(exc: Exception) -> bool:
    """상태 코드가 없을 때 연결/타임아웃류 예외를 이름으로 판별."""
    name = type(exc).__name__.lower()
    return any(k in name for k in ("timeout", "connect", "unavailable"))


def is_transient(exc: Exception) -> bool:
    code = getattr(exc, "code", None) or getattr(exc, "status_code", None)
    return code in TRANSIENT_CODES or _looks_transient(exc)


def build_client(
    *,
    api_key: str,
    timeout: float,
    use_vertex: bool | None,
    project: str = "",
    location: str = "global",
) -> object:
    """google-genai Client 생성(네트워크 없음). 벤더 임포트는 여기서만."""
    try:
        from google import genai
    except ImportError as exc:  # pragma: no cover - 설치 안내
        raise ProviderConfigError(
            "google-genai 가 설치되어 있지 않다. requirements.txt 참조."
        ) from exc

    http_options = {"timeout": int(timeout * 1000)}  # google-genai 는 ms 단위
    if resolve_vertex(use_vertex, api_key):
        # Express 모드: 프로젝트/리전은 API 키에 인코딩됨(project/location 불필요).
        kwargs: dict[str, object] = {
            "vertexai": True,
            "api_key": api_key,
            "http_options": http_options,
        }
        if project:  # 비-express Vertex(서비스계정 등)일 때만.
            kwargs.update(project=project, location=location)
            kwargs.pop("api_key")
        return genai.Client(**kwargs)
    return genai.Client(api_key=api_key, http_options=http_options)


def run_with_retry(fn: Callable[[], T], max_retries: int) -> T:
    """일시적 오류(429/5xx·연결/타임아웃)면 재시도, 그 외는 즉시 ProviderError."""
    last_exc: Exception | None = None
    for attempt in range(max_retries + 1):
        try:
            return fn()
        except Exception as exc:  # google.genai.errors.APIError 등
            if is_transient(exc) and attempt < max_retries:
                last_exc = exc
                continue
            raise ProviderError(f"Gemini 호출 실패: {exc}") from exc
    raise ProviderError(f"Gemini 호출 실패(재시도 소진): {last_exc}")  # 방어적
