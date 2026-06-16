"""/ai/suggest-highlights I/O 스키마 (명세서 §5.5)."""

from __future__ import annotations

from pydantic import BaseModel, Field

# 한 문서에서 제안할 하이라이트 기본 상한.
DEFAULT_HIGHLIGHT_LIMIT = 10


class SuggestHighlightsRequest(BaseModel):
    """입력: { documentId, limit? }. 본문은 document_chunks에서 읽는다."""

    document_id: int = Field(..., alias="documentId")
    limit: int = Field(DEFAULT_HIGHLIGHT_LIMIT, ge=1, le=50)

    model_config = {"populate_by_name": True}


class Highlight(BaseModel):
    """핵심 문장 제안. pageNo는 원문 청크에서 검증된 실제 페이지."""

    page_no: int | None = Field(None, serialization_alias="pageNo")
    text: str
    reason: str

    model_config = {"populate_by_name": True}
