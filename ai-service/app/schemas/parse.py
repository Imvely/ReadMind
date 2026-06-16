"""/ai/parse I/O 스키마 (명세서 §5.2). 모든 I/O는 pydantic으로 검증."""

from __future__ import annotations

from pydantic import BaseModel, Field


class ParseRequest(BaseModel):
    """입력: { documentId, storageKey, format }."""

    document_id: int = Field(..., alias="documentId")
    storage_key: str = Field(..., alias="storageKey", min_length=1)
    format: str = Field(..., min_length=1)

    model_config = {"populate_by_name": True}


class ParseResponse(BaseModel):
    """출력: { chunkCount, language, pageCount }."""

    chunk_count: int = Field(..., serialization_alias="chunkCount")
    language: str | None = Field(None, serialization_alias="language")
    page_count: int = Field(..., serialization_alias="pageCount")

    model_config = {"populate_by_name": True}
