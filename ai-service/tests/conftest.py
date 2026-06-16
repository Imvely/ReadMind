"""파싱 테스트 공용 픽스처/페이크."""

from __future__ import annotations

import fitz  # PyMuPDF

from app.parse.ports import ChunkRecord


def make_pdf(pages_text: list[str]) -> bytes:
    """주어진 페이지 텍스트로 실제 PDF 바이트를 생성."""
    doc = fitz.open()
    for text in pages_text:
        page = doc.new_page()
        page.insert_text((72, 72), text, fontsize=11)
    data = doc.tobytes()
    doc.close()
    return data


class FakeStorage:
    def __init__(self, data: bytes) -> None:
        self._data = data
        self.calls: list[str] = []

    def download(self, storage_key: str) -> bytes:
        self.calls.append(storage_key)
        return self._data


class FakeEmbedder:
    """차원 dim의 더미 벡터를 입력 개수만큼 반환."""

    def __init__(self, dim: int = 1024) -> None:
        self.dim = dim
        self.embedded: list[str] = []

    def embed(self, texts: list[str]) -> list[list[float]]:
        self.embedded.extend(texts)
        return [[0.01 * (i + 1)] * self.dim for i in range(len(texts))]


class FakeRepo:
    def __init__(self) -> None:
        self.saved: dict[int, list[ChunkRecord]] = {}

    def replace_document_chunks(
        self, document_id: int, chunks: list[ChunkRecord]
    ) -> None:
        self.saved[document_id] = chunks
