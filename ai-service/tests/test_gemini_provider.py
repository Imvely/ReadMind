"""GeminiLLM 테스트 — 페이크 client 주입으로 google-genai/네트워크 없이 검증."""

from __future__ import annotations

import pytest

from app.core.config import Settings
from app.providers import (
    GeminiLLM,
    LLMProvider,
    ProviderConfigError,
    ProviderError,
    get_llm_provider,
)
from app.providers._gemini import resolve_vertex


class _Resp:
    def __init__(self, text: str | None, prompt_feedback: object = None) -> None:
        self.text = text
        self.prompt_feedback = prompt_feedback


class _ApiError(Exception):
    """google.genai.errors.APIError 흉내(code 속성 보유)."""

    def __init__(self, code: int, message: str = "err") -> None:
        super().__init__(message)
        self.code = code


class _FakeModels:
    def __init__(self, script) -> None:
        self._script = list(script)
        self.calls: list[dict] = []

    def generate_content(self, *, model, contents, config):
        self.calls.append({"model": model, "contents": contents, "config": config})
        item = self._script.pop(0)
        if isinstance(item, Exception):
            raise item
        return item


class _FakeClient:
    def __init__(self, script) -> None:
        self.models = _FakeModels(script)


def _gemini(script, **kwargs) -> GeminiLLM:
    client = _FakeClient(script)
    return GeminiLLM(
        api_key="AQ.test", model="gemini-2.5-flash", client=client, **kwargs
    )


# ── 해피패스 ──
def test_complete_returns_text():
    llm = _gemini([_Resp("안녕하세요")])
    assert llm.complete("sys", "user") == "안녕하세요"


def test_json_mode_and_system_instruction_passed():
    client = _FakeClient([_Resp("{}")])
    llm = GeminiLLM(api_key="AQ.k", model="gemini-2.5-flash", client=client)
    llm.complete("너는 도우미", "질문", json_mode=True)
    call = client.models.calls[0]
    assert call["model"] == "gemini-2.5-flash"
    assert call["contents"] == "질문"
    assert call["config"]["system_instruction"] == "너는 도우미"
    assert call["config"]["response_mime_type"] == "application/json"


def test_non_json_mode_omits_mime_type():
    client = _FakeClient([_Resp("plain")])
    GeminiLLM(api_key="AQ.k", model="m", client=client).complete("s", "u")
    assert "response_mime_type" not in client.models.calls[0]["config"]


def test_implements_protocol():
    assert isinstance(_gemini([_Resp("ok")]), LLMProvider)


# ── 실패케이스 ──
def test_missing_config_raises():
    with pytest.raises(ProviderConfigError):
        GeminiLLM(api_key="", model="m", client=_FakeClient([]))
    with pytest.raises(ProviderConfigError):
        GeminiLLM(api_key="AQ.k", model="", client=_FakeClient([]))


def test_retries_on_transient_then_succeeds():
    client = _FakeClient([_ApiError(503), _Resp("ok")])
    llm = GeminiLLM(api_key="AQ.k", model="m", max_retries=2, client=client)
    assert llm.complete("s", "u") == "ok"
    assert len(client.models.calls) == 2


def test_non_transient_error_raises_immediately():
    client = _FakeClient([_ApiError(401, "401 UNAUTHENTICATED"), _Resp("nope")])
    llm = GeminiLLM(api_key="AQ.k", model="m", max_retries=2, client=client)
    with pytest.raises(ProviderError, match="401"):
        llm.complete("s", "u")
    assert len(client.models.calls) == 1  # 재시도 안 함


def test_empty_text_raises_provider_error():
    llm = _gemini([_Resp(None, prompt_feedback="BLOCKED")])
    with pytest.raises(ProviderError, match="텍스트가 없다"):
        llm.complete("s", "u")


def test_retry_exhausted_raises():
    llm = _gemini([_ApiError(503), _ApiError(503)], max_retries=1)
    with pytest.raises(ProviderError):
        llm.complete("s", "u")


# ── Vertex 자동감지 ──
def test_resolve_vertex_detects_express_key():
    assert resolve_vertex(None, "AQ.abc") is True
    assert resolve_vertex(None, "AIzaSyABC") is False


def test_resolve_vertex_explicit_overrides():
    assert resolve_vertex(True, "AIzaSyABC") is True
    assert resolve_vertex(False, "AQ.abc") is False


# ── 팩토리(환경변수 선택) ──
def test_factory_selects_gemini(monkeypatch):
    """llm_provider=gemini → GeminiLLM. 실제 client 생성은 막는다(SDK/네트워크 무관)."""
    created: dict = {}

    def fake_init(self, **kwargs):
        created.update(kwargs)
        self._model = kwargs["model"]
        self._max_retries = kwargs.get("max_retries", 2)
        self._client = _FakeClient([_Resp("x")])

    monkeypatch.setattr(GeminiLLM, "__init__", fake_init)
    cfg = Settings(
        llm_provider="gemini",
        llm_api_key="AQ.secret",
        llm_model="gemini-2.5-flash",
    )
    provider = get_llm_provider(cfg)
    assert isinstance(provider, GeminiLLM)
    assert created["api_key"] == "AQ.secret"
    assert created["model"] == "gemini-2.5-flash"
    assert created["use_vertex"] is None  # 자동감지 위임
