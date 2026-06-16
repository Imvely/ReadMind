"""청킹 테스트 — 크기/오버랩/page_no 보존."""

from __future__ import annotations

from app.chunking.splitter import chunk_pages
from app.chunking.tokens import estimate_tokens
from app.parsers.base import Page


def test_short_doc_single_chunk():
    pages = [Page(page_no=1, text="짧은 문장. 하나의 청크.")]
    chunks = chunk_pages(pages)
    assert len(chunks) == 1
    assert chunks[0].chunk_index == 0
    assert chunks[0].page_no == 1


def test_chunks_respect_target_size():
    # 여러 문단으로 큰 문서 구성.
    para = ("word " * 120).strip()  # ~120 토큰 문단
    text = "\n\n".join([para] * 10)
    pages = [Page(page_no=1, text=text)]
    chunks = chunk_pages(pages, target_tokens=200, overlap_tokens=20)
    assert len(chunks) > 1
    # 각 청크는 목표 + 한 문단 여유 안.
    for c in chunks:
        assert c.token_count <= 200 + 120


def test_page_no_preserved_across_pages():
    pages = [
        Page(page_no=1, text=("alpha " * 250).strip()),
        Page(page_no=2, text=("beta " * 250).strip()),
    ]
    chunks = chunk_pages(pages, target_tokens=200, overlap_tokens=0)
    page1 = [c for c in chunks if c.page_no == 1]
    page2 = [c for c in chunks if c.page_no == 2]
    assert page1 and page2
    # 2페이지 청크는 beta 내용을 담는다.
    assert any("beta" in c.content for c in page2)


def test_overlap_carries_context():
    para = ("lorem " * 80).strip()
    text = "\n\n".join([para] * 6)
    pages = [Page(page_no=1, text=text)]
    no_ov = chunk_pages(pages, target_tokens=160, overlap_tokens=0)
    ov = chunk_pages(pages, target_tokens=160, overlap_tokens=40)
    # 오버랩이 있으면 동일 목표에서 청크 수가 같거나 더 많다(맥락 이월).
    assert len(ov) >= len(no_ov)


def test_oversized_single_paragraph_hard_split():
    # 경계 없는 한 문단이 목표를 크게 초과 → 하드 분할.
    big = "word " * 1000
    pages = [Page(page_no=3, text=big)]
    chunks = chunk_pages(pages, target_tokens=200, overlap_tokens=0)
    assert len(chunks) > 1
    assert all(c.page_no == 3 for c in chunks)
    assert all(estimate_tokens(c.content) <= 200 + 5 for c in chunks)


def test_empty_pages_yield_no_chunks():
    assert chunk_pages([Page(page_no=1, text="   \n\n  ")]) == []
