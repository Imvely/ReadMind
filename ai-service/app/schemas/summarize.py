"""/ai/summarize I/O 스키마 (명세서 §5.3).

PAPER 스타일은 고정 JSON. LLM 출력을 이 모델로 검증해 누락 필드를 막는다
(스키마 강제 = 프론트 계약 안정).
"""

from __future__ import annotations

from enum import StrEnum

from pydantic import BaseModel, Field


class SummaryStyle(StrEnum):
    PAPER = "PAPER"
    PLAIN = "PLAIN"


class SummarizeRequest(BaseModel):
    """입력: { documentId, style }. 본문은 document_chunks에서 읽는다(재파싱 X)."""

    document_id: int = Field(..., alias="documentId")
    style: SummaryStyle = SummaryStyle.PAPER

    model_config = {"populate_by_name": True}


class PaperStructure(BaseModel):
    objective: str
    method: str
    results: str
    limitations: str
    contribution: str


class GlossaryItem(BaseModel):
    term: str
    desc: str


class PaperSummary(BaseModel):
    """PAPER 스타일 고정 출력."""

    tldr: str
    structure: PaperStructure
    keypoints: list[str]
    glossary: list[GlossaryItem]


class PlainSummary(BaseModel):
    """PLAIN 스타일 출력."""

    tldr: str
    keypoints: list[str]
