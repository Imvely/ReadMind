"""파싱 파이프라인 통합 테스트 — 포트는 페이크로 주입(인프라 불필요)."""

from __future__ import annotations

import pytest

from app.parse.pipeline import run_parse
from app.parsers.base import ParserError, UnsupportedFormatError
from app.schemas.parse import ParseRequest
from tests.conftest import FakeEmbedder, FakeRepo, FakeStorage, make_pdf


def _req(fmt: str = "pdf") -> ParseRequest:
    return ParseRequest(documentId=42, storageKey="docs/42.pdf", format=fmt)


def test_happy_path_stores_chunks_and_returns_counts():
    pdf = make_pdf(["Introduction. " * 60, "Methods section. " * 60])
    storage, repo, emb = FakeStorage(pdf), FakeRepo(), FakeEmbedder(dim=1024)

    resp = run_parse(_req(), storage=storage, repo=repo, embedder=emb)

    assert resp.chunk_count > 0
    assert resp.page_count == 2
    assert resp.language == "en"
    # 저장된 청크 = 응답 chunk_count, 인덱스 연속, page_no 보존, 임베딩 차원 1024.
    saved = repo.saved[42]
    assert len(saved) == resp.chunk_count
    assert [c.chunk_index for c in saved] == list(range(len(saved)))
    assert all(c.page_no in (1, 2) for c in saved)
    assert all(len(c.embedding) == 1024 for c in saved)
    assert storage.calls == ["docs/42.pdf"]


def test_unsupported_format_propagates():
    storage, repo, emb = FakeStorage(b"x"), FakeRepo(), FakeEmbedder()
    with pytest.raises(UnsupportedFormatError):
        run_parse(_req("docx"), storage=storage, repo=repo, embedder=emb)
    assert repo.saved == {}


def test_textless_pdf_raises_and_saves_nothing():
    storage = FakeStorage(make_pdf(["", ""]))
    repo, emb = FakeRepo(), FakeEmbedder()
    with pytest.raises(ParserError):
        run_parse(_req(), storage=storage, repo=repo, embedder=emb)
    assert repo.saved == {}
