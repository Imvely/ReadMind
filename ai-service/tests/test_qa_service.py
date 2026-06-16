"""RAG Q&A 서비스 테스트 — 환각 방지(인용 교차검증) 중심."""

from __future__ import annotations

import json

import pytest

from app.rag.errors import QaError
from app.rag.qa import NO_EVIDENCE, answer_question
from app.schemas.qa import QaRequest
from tests.conftest import FakeEmbedder, FakeLLM, FakeRetriever, retrieved


def _llm(answer: str, citations: list) -> FakeLLM:
    return FakeLLM(json_response=json.dumps({"answer": answer, "citations": citations}))


def _req(question: str = "핵심 기여는?") -> QaRequest:
    return QaRequest(documentId=1, question=question)


_HITS = retrieved(
    [
        (5, 2, "The model introduces a new attention mechanism."),
        (9, 4, "It is evaluated on three benchmarks."),
    ]
)


def test_happy_path_returns_answer_and_sources():
    emb = FakeEmbedder(dim=1024)
    retr = FakeRetriever(_HITS)
    llm = _llm("새 어텐션 메커니즘을 제안한다.", [5])
    out = answer_question(_req(), retriever=retr, embedder=emb, llm=llm)
    assert out.answer == "새 어텐션 메커니즘을 제안한다."
    assert len(out.sources) == 1
    assert out.sources[0].chunk_index == 5
    assert out.sources[0].page_no == 2
    assert "attention" in out.sources[0].snippet
    # 질문이 임베딩되어 검색에 쓰였다.
    assert emb.embedded == ["핵심 기여는?"]
    assert retr.calls and retr.calls[0][0] == 1


def test_citation_not_in_hits_is_dropped():
    llm = _llm("답변", [5, 999])  # 999는 검색결과에 없음
    out = answer_question(
        _req(), retriever=FakeRetriever(_HITS), embedder=FakeEmbedder(), llm=llm
    )
    assert [s.chunk_index for s in out.sources] == [5]


def test_no_valid_citation_downgrades_to_no_evidence():
    # 발췌 밖 인용만 → 근거 없는 답변 금지(§3) → 모른다로 강등.
    llm = _llm("그럴듯한 답", [999])
    out = answer_question(
        _req(), retriever=FakeRetriever(_HITS), embedder=FakeEmbedder(), llm=llm
    )
    assert out.answer == NO_EVIDENCE
    assert out.sources == []


def test_no_hits_returns_no_evidence_without_calling_llm():
    llm = _llm("x", [1])
    out = answer_question(
        _req(), retriever=FakeRetriever([]), embedder=FakeEmbedder(), llm=llm
    )
    assert out.answer == NO_EVIDENCE
    assert out.sources == []
    assert llm.calls == []  # 근거 없으면 LLM 호출 안 함


def test_duplicate_citations_collapsed():
    llm = _llm("답", [5, 5])
    out = answer_question(
        _req(), retriever=FakeRetriever(_HITS), embedder=FakeEmbedder(), llm=llm
    )
    assert len(out.sources) == 1


def test_string_citations_coerced():
    llm = _llm("답", ["5", "9"])
    out = answer_question(
        _req(), retriever=FakeRetriever(_HITS), embedder=FakeEmbedder(), llm=llm
    )
    assert {s.chunk_index for s in out.sources} == {5, 9}


def test_non_json_raises_qa_error():
    llm = FakeLLM(json_response="죄송하지만 답할 수 없습니다")
    with pytest.raises(QaError):
        answer_question(
            _req(), retriever=FakeRetriever(_HITS), embedder=FakeEmbedder(), llm=llm
        )


def test_empty_answer_with_citation_raises():
    llm = _llm("", [5])
    with pytest.raises(QaError, match="빈 답변"):
        answer_question(
            _req(), retriever=FakeRetriever(_HITS), embedder=FakeEmbedder(), llm=llm
        )
