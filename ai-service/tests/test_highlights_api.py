"""/ai/suggest-highlights 라우터 테스트."""

from __future__ import annotations

import json

from fastapi.testclient import TestClient

from app.api.deps import get_chunk_reader, get_llm, verify_service_token
from app.main import app
from tests.conftest import FakeLLM, FakeReader, chunk_texts

_C1 = "Deep learning improves accuracy on benchmarks. Models need much data."


def _override(reader: FakeReader, llm: FakeLLM) -> TestClient:
    app.dependency_overrides[get_chunk_reader] = lambda: reader
    app.dependency_overrides[get_llm] = lambda: llm
    app.dependency_overrides[verify_service_token] = lambda: None
    return TestClient(app)


def teardown_function() -> None:
    app.dependency_overrides.clear()


def _llm() -> FakeLLM:
    return FakeLLM(
        json_response=json.dumps(
            {
                "highlights": [
                    {
                        "text": "Deep learning improves accuracy on benchmarks.",
                        "reason": "핵심",
                    }
                ]
            }
        )
    )


def test_suggest_highlights_happy_path():
    client = _override(FakeReader(chunk_texts([_C1])), _llm())
    resp = client.post(
        "/ai/suggest-highlights", json={"documentId": 1, "limit": 5}
    )
    assert resp.status_code == 200
    body = resp.json()
    assert isinstance(body, list) and len(body) == 1
    assert set(body[0]) == {"pageNo", "text", "reason"}
    assert body[0]["pageNo"] == 1


def test_no_chunks_returns_422():
    client = _override(FakeReader([]), _llm())
    resp = client.post("/ai/suggest-highlights", json={"documentId": 2})
    assert resp.status_code == 422


def test_llm_failure_returns_502():
    client = _override(
        FakeReader(chunk_texts([_C1])), FakeLLM(json_response="{}", raise_error=True)
    )
    resp = client.post("/ai/suggest-highlights", json={"documentId": 3})
    assert resp.status_code == 502


def test_invalid_limit_returns_422():
    client = _override(FakeReader(chunk_texts([_C1])), _llm())
    resp = client.post(
        "/ai/suggest-highlights", json={"documentId": 4, "limit": 0}
    )
    assert resp.status_code == 422
