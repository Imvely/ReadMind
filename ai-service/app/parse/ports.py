"""파싱 파이프라인이 의존하는 포트(인터페이스).

저장소·스토리지를 Protocol로 분리해 파이프라인을 인프라 없이 테스트하고,
구현체(S3/Postgres)는 교체 가능하게 한다.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, runtime_checkable


@dataclass(frozen=True)
class ChunkRecord:
    """document_chunks 한 행에 대응(명세서 §3)."""

    chunk_index: int
    page_no: int | None
    content: str
    token_count: int
    embedding: list[float]


@dataclass(frozen=True)
class ChunkText:
    """요약/RAG가 읽는 청크(임베딩 제외)."""

    chunk_index: int
    page_no: int | None
    content: str


@dataclass(frozen=True)
class RetrievedChunk:
    """벡터 검색 결과 청크 + 거리(작을수록 유사)."""

    chunk_index: int
    page_no: int | None
    content: str
    distance: float


@runtime_checkable
class Storage(Protocol):
    """S3 호환 객체 스토리지에서 원본 바이트를 가져온다."""

    def download(self, storage_key: str) -> bytes: ...


@runtime_checkable
class ChunkRepository(Protocol):
    """document_chunks 영속화."""

    def replace_document_chunks(
        self, document_id: int, chunks: list[ChunkRecord]
    ) -> None:
        """문서의 기존 청크를 지우고 새로 저장(재파싱 멱등)."""
        ...


@runtime_checkable
class ChunkReader(Protocol):
    """document_chunks 조회(요약·RAG용)."""

    def fetch_document_chunks(self, document_id: int) -> list[ChunkText]:
        """chunk_index 오름차순으로 문서의 청크 텍스트를 읽는다."""
        ...


@runtime_checkable
class ChunkRetriever(Protocol):
    """문서 내 벡터 유사도 검색(RAG용)."""

    def search_similar_chunks(
        self, document_id: int, embedding: list[float], k: int
    ) -> list[RetrievedChunk]:
        """질문 임베딩과 코사인 거리가 가까운 top-k 청크를 반환."""
        ...
