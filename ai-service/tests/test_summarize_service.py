"""요약 서비스 테스트 — FakeLLM/FakeReader로 실 LLM·DB 없이 검증."""

from __future__ import annotations

import pytest

from app.schemas.summarize import PaperSummary, PlainSummary, SummarizeRequest
from app.summarize.errors import SummarizeError
from app.summarize.service import summarize
from tests.conftest import (
    PAPER_JSON,
    PLAIN_JSON,
    FakeLLM,
    FakeReader,
    chunk_texts,
)


def _req(style: str = "PAPER", doc: int = 1) -> SummarizeRequest:
    return SummarizeRequest(documentId=doc, style=style)


def test_paper_single_pass():
    reader = FakeReader(chunk_texts(["짧은 본문입니다."]))
    llm = FakeLLM(json_response=PAPER_JSON)
    out = summarize(_req(), reader=reader, llm=llm)
    assert isinstance(out, PaperSummary)
    assert out.tldr == "한 줄 요약"
    assert out.structure.contribution == "기여"
    assert out.glossary[0].term == "용어"
    # 단일 호출: json 1회, map(note) 0회.
    assert llm.json_calls == 1
    assert llm.note_calls == 0


def test_paper_map_reduce_for_long_doc():
    # 여러 청크 + 작은 single_pass 한계 → map-reduce 경로.
    reader = FakeReader(chunk_texts([("word " * 100).strip() for _ in range(6)]))
    llm = FakeLLM(json_response=PAPER_JSON, note_response="- 부분요약")
    out = summarize(
        _req(),
        reader=reader,
        llm=llm,
        single_pass_tokens=50,
        map_group_tokens=120,
    )
    assert isinstance(out, PaperSummary)
    # reduce에서 json 1회, map에서 note 2회 이상.
    assert llm.json_calls == 1
    assert llm.note_calls >= 2


def test_plain_style():
    reader = FakeReader(chunk_texts(["본문"]))
    llm = FakeLLM(json_response=PLAIN_JSON)
    out = summarize(_req(style="PLAIN"), reader=reader, llm=llm)
    assert isinstance(out, PlainSummary)
    assert out.keypoints == ["a", "b"]


def test_fence_wrapped_json_is_parsed():
    reader = FakeReader(chunk_texts(["본문"]))
    llm = FakeLLM(json_response=f"```json\n{PAPER_JSON}\n```")
    out = summarize(_req(), reader=reader, llm=llm)
    assert isinstance(out, PaperSummary)


def test_no_chunks_raises():
    llm = FakeLLM(json_response=PAPER_JSON)
    with pytest.raises(SummarizeError, match="청크가 없다"):
        summarize(_req(), reader=FakeReader([]), llm=llm)
    assert llm.calls == []  # LLM 호출 전에 차단


def test_schema_mismatch_raises():
    # PAPER인데 structure 누락 → 스키마 검증 실패.
    reader = FakeReader(chunk_texts(["본문"]))
    llm = FakeLLM(json_response='{"tldr": "x", "keypoints": []}')
    with pytest.raises(SummarizeError, match="스키마"):
        summarize(_req(), reader=reader, llm=llm)


def test_non_json_output_raises():
    reader = FakeReader(chunk_texts(["본문"]))
    llm = FakeLLM(json_response="요약을 만들 수 없습니다")
    with pytest.raises(SummarizeError):
        summarize(_req(), reader=reader, llm=llm)
