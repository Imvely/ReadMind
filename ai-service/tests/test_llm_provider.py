"""LLMProvider 테스트 — httpx.MockTransport로 벤더 없이 검증."""

from __future__ import annotations

import httpx
import pytest

from app.core.config import Settings
from app.providers import (
    LLMProvider,
    OpenAICompatLLM,
    ProviderConfigError,
    ProviderError,
    get_llm_provider,
)


def _client(handler) -> httpx.Client:
    return httpx.Client(
        transport=httpx.MockTransport(handler), base_url="http://test.local"
    )


def _chat_ok(content: str):
    def handler(request: httpx.Request) -> httpx.Response:
        body = {"choices": [{"message": {"role": "assistant", "content": content}}]}
        return httpx.Response(200, json=body)

    return handler


# ── 해피패스 ──
def test_complete_returns_content():
    llm = OpenAICompatLLM(
        api_base="http://x", model="m", client=_client(_chat_ok("안녕"))
    )
    assert llm.complete("sys", "user") == "안녕"


def test_json_mode_sets_response_format():
    seen: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        import json

        seen.update(json.loads(request.content))
        return httpx.Response(
            200, json={"choices": [{"message": {"content": "{}"}}]}
        )

    llm = OpenAICompatLLM(api_base="http://x", model="m", client=_client(handler))
    llm.complete("sys", "user", json_mode=True)
    assert seen["response_format"] == {"type": "json_object"}
    assert seen["model"] == "m"


def test_implements_protocol():
    llm = OpenAICompatLLM(
        api_base="http://x", model="m", client=_client(_chat_ok("ok"))
    )
    assert isinstance(llm, LLMProvider)


# ── 실패케이스 ──
def test_missing_config_raises():
    with pytest.raises(ProviderConfigError):
        OpenAICompatLLM(api_base="", model="m")
    with pytest.raises(ProviderConfigError):
        OpenAICompatLLM(api_base="http://x", model="")


def test_http_4xx_raises_provider_error():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, text="unauthorized")

    llm = OpenAICompatLLM(api_base="http://x", model="m", client=_client(handler))
    with pytest.raises(ProviderError, match="401"):
        llm.complete("s", "u")


def test_retries_on_503_then_succeeds():
    calls = {"n": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        calls["n"] += 1
        if calls["n"] == 1:
            return httpx.Response(503, text="busy")
        return httpx.Response(200, json={"choices": [{"message": {"content": "ok"}}]})

    llm = OpenAICompatLLM(
        api_base="http://x", model="m", max_retries=2, client=_client(handler)
    )
    assert llm.complete("s", "u") == "ok"
    assert calls["n"] == 2


def test_unexpected_shape_raises():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"nope": True})

    llm = OpenAICompatLLM(api_base="http://x", model="m", client=_client(handler))
    with pytest.raises(ProviderError):
        llm.complete("s", "u")


# ── 팩토리(환경변수 선택) ──
def test_factory_builds_from_settings():
    cfg = Settings(
        llm_provider="selfhosted",
        llm_api_base="http://vllm:8000/v1",
        llm_model="qwen",
    )
    provider = get_llm_provider(cfg)
    assert isinstance(provider, OpenAICompatLLM)


def test_factory_missing_base_raises():
    cfg = Settings(llm_model="m")  # api_base 비어있음
    with pytest.raises(ProviderConfigError):
        get_llm_provider(cfg)
