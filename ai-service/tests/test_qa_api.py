"""/ai/qa 라우터 테스트."""

from __future__ import annotations

import json

from fastapi.testclient import TestClient

from app.api.deps import (
    get_chunk_retriever,
    get_embedder,
    get_llm,
    verify_service_token,
)
from app.main import app
from app.rag.qa import NO_EVIDENCE
from tests.conftest import FakeEmbedder, FakeLLM, FakeRetriever, retrieved

_HITS = retrieved([(3, 1, "Self-supervised pretraining boosts downstream tasks.")])


def _override(retr: FakeRetriever, llm: FakeLLM) -> TestClient:
    app.dependency_overrides[get_chunk_retriever] = lambda: retr
    app.dependency_overrides[get_embedder] = lambda: FakeEmbedder(dim=1024)
    app.dependency_overrides[get_llm] = lambda: llm
    app.dependency_overrides[verify_service_token] = lambda: None
    return TestClient(app)


def teardown_function() -> None:
    app.dependency_overrides.clear()


def _llm(answer: str, citations: list) -> FakeLLM:
    return FakeLLM(json_response=json.dumps({"answer": answer, "citations": citations}))


def test_qa_happy_path_returns_aliased_sources():
    client = _override(FakeRetriever(_HITS), _llm("사전학습이 성능을 높인다.", [3]))
    resp = client.post(
        "/ai/qa", json={"documentId": 1, "question": "사전학습 효과는?"}
    )
    assert resp.status_code == 200
    body = resp.json()
    assert set(body) == {"answer", "sources"}
    assert body["sources"][0].keys() >= {"chunkIndex", "pageNo", "snippet"}
    assert body["sources"][0]["chunkIndex"] == 3
    assert body["sources"][0]["pageNo"] == 1


def test_qa_no_hits_returns_grounded_no_evidence():
    client = _override(FakeRetriever([]), _llm("x", [1]))
    resp = client.post("/ai/qa", json={"documentId": 2, "question": "무엇?"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["answer"] == NO_EVIDENCE
    assert body["sources"] == []


def test_qa_missing_question_returns_422():
    client = _override(FakeRetriever(_HITS), _llm("a", [3]))
    resp = client.post("/ai/qa", json={"documentId": 3})
    assert resp.status_code == 422


def test_qa_llm_failure_returns_502():
    client = _override(
        FakeRetriever(_HITS), FakeLLM(json_response="{}", raise_error=True)
    )
    resp = client.post("/ai/qa", json={"documentId": 4, "question": "왜?"})
    assert resp.status_code == 502


def test_qa_accepts_history():
    client = _override(FakeRetriever(_HITS), _llm("이어진 답변.", [3]))
    resp = client.post(
        "/ai/qa",
        json={
            "documentId": 5,
            "question": "그럼 한계는?",
            "history": [{"role": "user", "content": "효과는?"}],
        },
    )
    assert resp.status_code == 200
