"""/ai/summarize 라우터 테스트."""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.api.deps import get_chunk_reader, get_llm, verify_service_token
from app.main import app
from tests.conftest import PAPER_JSON, FakeLLM, FakeReader, chunk_texts


def _override(reader: FakeReader, llm: FakeLLM) -> TestClient:
    app.dependency_overrides[get_chunk_reader] = lambda: reader
    app.dependency_overrides[get_llm] = lambda: llm
    app.dependency_overrides[verify_service_token] = lambda: None
    return TestClient(app)


def teardown_function() -> None:
    app.dependency_overrides.clear()


def test_summarize_paper_happy_path():
    client = _override(FakeReader(chunk_texts(["본문 내용."])), FakeLLM(PAPER_JSON))
    resp = client.post("/ai/summarize", json={"documentId": 1, "style": "PAPER"})
    assert resp.status_code == 200
    body = resp.json()
    assert set(body) == {"tldr", "structure", "keypoints", "glossary"}
    assert set(body["structure"]) == {
        "objective",
        "method",
        "results",
        "limitations",
        "contribution",
    }
    assert body["glossary"][0]["term"] == "용어"


def test_summarize_defaults_to_paper():
    client = _override(FakeReader(chunk_texts(["본문"])), FakeLLM(PAPER_JSON))
    resp = client.post("/ai/summarize", json={"documentId": 9})
    assert resp.status_code == 200
    assert "structure" in resp.json()


def test_summarize_no_chunks_returns_422():
    client = _override(FakeReader([]), FakeLLM(PAPER_JSON))
    resp = client.post("/ai/summarize", json={"documentId": 2, "style": "PAPER"})
    assert resp.status_code == 422


def test_summarize_llm_failure_returns_502():
    client = _override(
        FakeReader(chunk_texts(["본문"])), FakeLLM(PAPER_JSON, raise_error=True)
    )
    resp = client.post("/ai/summarize", json={"documentId": 3, "style": "PAPER"})
    assert resp.status_code == 502


def test_summarize_invalid_style_returns_422():
    client = _override(FakeReader(chunk_texts(["본문"])), FakeLLM(PAPER_JSON))
    resp = client.post("/ai/summarize", json={"documentId": 4, "style": "BOGUS"})
    assert resp.status_code == 422
