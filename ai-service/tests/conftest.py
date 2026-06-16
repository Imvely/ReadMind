"""파싱 테스트 공용 픽스처/페이크."""

from __future__ import annotations

import fitz  # PyMuPDF

from app.parse.ports import ChunkRecord, ChunkText, RetrievedChunk
from app.providers.errors import ProviderError


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


class FakeReader:
    """fetch_document_chunks용 페이크."""

    def __init__(self, chunks: list[ChunkText]) -> None:
        self._chunks = chunks
        self.calls: list[int] = []

    def fetch_document_chunks(self, document_id: int) -> list[ChunkText]:
        self.calls.append(document_id)
        return self._chunks


class FakeLLM:
    """json_mode면 json_response를, 아니면 note_response를 반환. 호출 기록 보존.

    raise_error를 주면 complete가 ProviderError를 던진다(실패 경로 테스트).
    """

    def __init__(
        self,
        json_response: str = "{}",
        note_response: str = "- note",
        raise_error: bool = False,
    ) -> None:
        self.json_response = json_response
        self.note_response = note_response
        self.raise_error = raise_error
        self.calls: list[tuple[str, str, bool]] = []

    def complete(self, system: str, user: str, *, json_mode: bool = False) -> str:
        self.calls.append((system, user, json_mode))
        if self.raise_error:
            raise ProviderError("LLM 다운")
        return self.json_response if json_mode else self.note_response

    @property
    def json_calls(self) -> int:
        return sum(1 for _, _, jm in self.calls if jm)

    @property
    def note_calls(self) -> int:
        return sum(1 for _, _, jm in self.calls if not jm)


class FakeRetriever:
    """search_similar_chunks용 페이크. 미리 준비한 hits를 top-k로 반환."""

    def __init__(self, hits: list[RetrievedChunk]) -> None:
        self._hits = hits
        self.calls: list[tuple[int, int]] = []

    def search_similar_chunks(
        self, document_id: int, embedding: list[float], k: int
    ) -> list[RetrievedChunk]:
        self.calls.append((document_id, k))
        return self._hits[:k]


def chunk_texts(contents: list[str]) -> list[ChunkText]:
    return [
        ChunkText(chunk_index=i, page_no=i + 1, content=c)
        for i, c in enumerate(contents)
    ]


def retrieved(items: list[tuple[int, int | None, str]]) -> list[RetrievedChunk]:
    """(chunk_index, page_no, content) 튜플 목록 → RetrievedChunk 목록."""
    return [
        RetrievedChunk(chunk_index=ci, page_no=pg, content=c, distance=0.1 * n)
        for n, (ci, pg, c) in enumerate(items)
    ]


PAPER_JSON = (
    '{"tldr": "한 줄 요약", '
    '"structure": {"objective": "목적", "method": "방법", "results": "결과", '
    '"limitations": "한계", "contribution": "기여"}, '
    '"keypoints": ["요점1", "요점2"], '
    '"glossary": [{"term": "용어", "desc": "설명"}]}'
)
PLAIN_JSON = '{"tldr": "쉬운 요약", "keypoints": ["a", "b"]}'
