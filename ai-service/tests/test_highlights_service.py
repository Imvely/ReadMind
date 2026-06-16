"""핵심 문장 추출 서비스 테스트 — 원문 대조/환각 제거 검증."""

from __future__ import annotations

import json

import pytest

from app.highlights.errors import HighlightError
from app.highlights.service import suggest_highlights
from app.schemas.highlights import SuggestHighlightsRequest
from tests.conftest import FakeLLM, FakeReader, chunk_texts

_S1 = "Deep learning improves accuracy on benchmarks."
_S2 = "Transfer learning reduces the data requirement significantly."
_C1 = f"{_S1} Models need lots of data."
_C2 = f"{_S2} It is used."


def _reader() -> FakeReader:
    return FakeReader(chunk_texts([_C1, _C2]))  # page 1, 2


def _llm(highlights: list[dict]) -> FakeLLM:
    return FakeLLM(json_response=json.dumps({"highlights": highlights}))


def _req(limit: int = 10) -> SuggestHighlightsRequest:
    return SuggestHighlightsRequest(documentId=1, limit=limit)


def test_happy_path_pageno_from_source():
    llm = _llm(
        [
            {"text": _S1, "reason": "성과"},
            {"text": _S2, "reason": "방법"},
        ]
    )
    out = suggest_highlights(_req(), reader=_reader(), llm=llm)
    assert len(out) == 2
    assert out[0].page_no == 1 and out[0].reason == "성과"
    assert out[1].page_no == 2  # 2페이지 청크에서 검증된 페이지
    assert out[1].text.startswith("Transfer learning")


def test_hallucinated_sentence_dropped():
    llm = _llm(
        [
            {"text": _S1, "reason": "진짜"},
            {"text": "Not in the document at all.", "reason": "가짜"},
        ]
    )
    out = suggest_highlights(_req(), reader=_reader(), llm=llm)
    assert len(out) == 1
    assert out[0].reason == "진짜"


def test_limit_respected():
    llm = _llm(
        [
            {"text": _S1, "reason": "a"},
            {"text": _S2, "reason": "b"},
        ]
    )
    out = suggest_highlights(_req(limit=1), reader=_reader(), llm=llm)
    assert len(out) == 1


def test_duplicates_collapsed():
    llm = _llm(
        [
            {"text": _S1, "reason": "1"},
            {"text": _S1, "reason": "2"},
        ]
    )
    out = suggest_highlights(_req(), reader=_reader(), llm=llm)
    assert len(out) == 1


def test_too_short_text_skipped():
    # "Models"는 본문에 있지만 너무 짧아(정규화<8) 제외.
    llm = _llm([{"text": "Models", "reason": "짧음"}])
    out = suggest_highlights(_req(), reader=_reader(), llm=llm)
    assert out == []


def test_no_chunks_raises():
    llm = _llm([{"text": "x", "reason": "y"}])
    with pytest.raises(HighlightError, match="청크가 없다"):
        suggest_highlights(_req(), reader=FakeReader([]), llm=llm)
    assert llm.calls == []


def test_highlights_not_a_list_raises():
    llm = FakeLLM(json_response='{"highlights": "nope"}')
    with pytest.raises(HighlightError, match="배열이 아니다"):
        suggest_highlights(_req(), reader=_reader(), llm=llm)


def test_non_json_raises_highlight_error():
    llm = FakeLLM(json_response="요약 불가")
    with pytest.raises(HighlightError):
        suggest_highlights(_req(), reader=_reader(), llm=llm)
