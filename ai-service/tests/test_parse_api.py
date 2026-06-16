"""/ai/parse 라우터 테스트 — dependency_overrides로 페이크 주입."""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.api.deps import (
    get_chunk_repository,
    get_embedder,
    get_storage,
    verify_service_token,
)
from app.main import app
from tests.conftest import FakeEmbedder, FakeRepo, FakeStorage, make_pdf


def _client(pdf: bytes) -> tuple[TestClient, FakeRepo]:
    repo = FakeRepo()
    app.dependency_overrides[get_storage] = lambda: FakeStorage(pdf)
    app.dependency_overrides[get_chunk_repository] = lambda: repo
    app.dependency_overrides[get_embedder] = lambda: FakeEmbedder(dim=1024)
    app.dependency_overrides[verify_service_token] = lambda: None
    return TestClient(app), repo


def teardown_function() -> None:
    app.dependency_overrides.clear()


def test_health():
    assert TestClient(app).get("/health").json() == {"status": "ok"}


def test_parse_happy_path_returns_aliased_json():
    client, repo = _client(make_pdf(["Hello world. " * 50]))
    resp = client.post(
        "/ai/parse",
        json={"documentId": 7, "storageKey": "k.pdf", "format": "pdf"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert set(body) == {"chunkCount", "language", "pageCount"}
    assert body["chunkCount"] >= 1
    assert body["pageCount"] == 1
    assert repo.saved[7]


def test_parse_unsupported_format_returns_415():
    client, _ = _client(b"x")
    resp = client.post(
        "/ai/parse",
        json={"documentId": 1, "storageKey": "k.docx", "format": "docx"},
    )
    assert resp.status_code == 415


def test_parse_validation_error_returns_422():
    client, _ = _client(b"x")
    resp = client.post("/ai/parse", json={"documentId": 1})  # storageKey/format 누락
    assert resp.status_code == 422


def test_service_token_enforced_when_configured():
    from app.core.config import Settings, get_settings

    pdf = make_pdf(["hi there. " * 30])
    repo = FakeRepo()
    app.dependency_overrides[get_storage] = lambda: FakeStorage(pdf)
    app.dependency_overrides[get_chunk_repository] = lambda: repo
    app.dependency_overrides[get_embedder] = lambda: FakeEmbedder(dim=1024)
    app.dependency_overrides[get_settings] = lambda: Settings(ai_service_token="secret")
    try:
        client = TestClient(app)
        # 토큰 없음 → 401
        no_tok = client.post(
            "/ai/parse",
            json={"documentId": 2, "storageKey": "k.pdf", "format": "pdf"},
        )
        assert no_tok.status_code == 401
        # 올바른 토큰 → 200
        ok = client.post(
            "/ai/parse",
            json={"documentId": 2, "storageKey": "k.pdf", "format": "pdf"},
            headers={"X-Service-Token": "secret"},
        )
        assert ok.status_code == 200
    finally:
        app.dependency_overrides.clear()
