"""/ai/translate 테스트 — FakeLLM으로 벤더/네트워크 없이 검증."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.api.deps import get_llm, verify_service_token
from app.main import app
from app.providers.errors import ProviderError
from app.schemas.translate import TranslateRequest
from app.translate.errors import TranslateError
from app.translate.service import MAX_CHARS, translate


class FakeLLM:
    def __init__(self, out: str = "번역결과", raises: Exception | None = None) -> None:
        self.out = out
        self.raises = raises
        self.calls: list[dict] = []

    def complete(self, system: str, user: str, *, json_mode: bool = False) -> str:
        self.calls.append({"system": system, "user": user, "json_mode": json_mode})
        if self.raises:
            raise self.raises
        return self.out


# ── 서비스 ──
def test_translate_happy_returns_source_and_translation():
    llm = FakeLLM(out="This is a test.")
    res = translate(
        TranslateRequest(text="이것은 테스트다.", target_lang="en"), llm=llm
    )
    assert res.translated == "This is a test."
    assert res.source_excerpt == "이것은 테스트다."  # 원문대조
    assert res.target_lang == "en"
    # 번역은 자유 텍스트라 json_mode 를 쓰지 않는다.
    assert llm.calls[0]["json_mode"] is False


def test_translate_system_prompt_has_target_language():
    llm = FakeLLM()
    translate(TranslateRequest(text="hi", target_lang="ko"), llm=llm)
    assert "한국어" in llm.calls[0]["system"]


def test_translate_blank_text_raises():
    with pytest.raises(TranslateError):
        translate(TranslateRequest(text="   "), llm=FakeLLM())


def test_translate_too_long_raises():
    with pytest.raises(TranslateError, match="너무 길다"):
        translate(TranslateRequest(text="a" * (MAX_CHARS + 1)), llm=FakeLLM())


def test_translate_empty_result_raises():
    with pytest.raises(TranslateError):
        translate(TranslateRequest(text="hi"), llm=FakeLLM(out="  "))


# ── 라우터 ──
def _client(llm: FakeLLM) -> TestClient:
    app.dependency_overrides[get_llm] = lambda: llm
    app.dependency_overrides[verify_service_token] = lambda: None
    return TestClient(app)


def teardown_function() -> None:
    app.dependency_overrides.clear()


def test_api_translate_200_aliased():
    client = _client(FakeLLM(out="Hello"))
    resp = client.post("/ai/translate", json={"text": "안녕", "targetLang": "en"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["translated"] == "Hello"
    assert body["sourceExcerpt"] == "안녕"  # camelCase alias 출력
    assert body["targetLang"] == "en"


def test_api_translate_default_target_ko():
    llm = FakeLLM(out="번역")
    resp = _client(llm).post("/ai/translate", json={"text": "hello"})
    assert resp.status_code == 200
    assert "한국어" in llm.calls[0]["system"]  # 기본 targetLang=ko


def test_api_translate_empty_text_422():
    resp = _client(FakeLLM()).post("/ai/translate", json={"text": ""})
    assert resp.status_code == 422  # pydantic min_length


def test_api_translate_provider_error_502():
    resp = _client(FakeLLM(raises=ProviderError("down"))).post(
        "/ai/translate", json={"text": "hi"}
    )
    assert resp.status_code == 502
