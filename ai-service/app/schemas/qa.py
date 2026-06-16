"""/ai/qa I/O 스키마 (명세서 §5.4). RAG Q&A — sources 필수."""

from __future__ import annotations

from pydantic import BaseModel, Field


class QaTurn(BaseModel):
    """대화 이력 한 턴."""

    role: str
    content: str


class QaRequest(BaseModel):
    """입력: { documentId, question, history? }."""

    document_id: int = Field(..., alias="documentId")
    question: str = Field(..., min_length=1)
    history: list[QaTurn] = Field(default_factory=list)

    model_config = {"populate_by_name": True}


class Source(BaseModel):
    """답변 근거. 검색된 실제 청크에서만 생성된다(환각 방지)."""

    chunk_index: int = Field(..., serialization_alias="chunkIndex")
    page_no: int | None = Field(None, serialization_alias="pageNo")
    snippet: str

    model_config = {"populate_by_name": True}


class QaResponse(BaseModel):
    """출력: { answer, sources }. sources는 항상 포함(빈 배열 가능)."""

    answer: str
    sources: list[Source]
