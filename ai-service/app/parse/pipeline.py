"""/ai/parse 오케스트레이션 (명세서 §5.2).

S3 다운로드 → 포맷 디스패치 → 청킹 → 임베딩 → document_chunks 저장 → 결과.

documents.language/page_count/parse_status 갱신은 백엔드(be-doc-upload, §4.2)가
소유한다. 이 서비스는 {chunkCount, language, pageCount}를 반환하고 청크만 저장한다.
"""

from __future__ import annotations

from collections.abc import Callable

from app.chunking.splitter import chunk_pages
from app.core.lang import detect_language
from app.parse.ports import ChunkRecord, ChunkRepository, Storage
from app.parsers import ParsedDoc, ParserError, parse_document
from app.providers.embedding import EmbeddingProvider
from app.schemas.parse import ParseRequest, ParseResponse

Dispatch = Callable[[str, bytes], ParsedDoc]


def run_parse(
    req: ParseRequest,
    *,
    storage: Storage,
    repo: ChunkRepository,
    embedder: EmbeddingProvider,
    dispatch: Dispatch = parse_document,
) -> ParseResponse:
    data = storage.download(req.storage_key)
    parsed = dispatch(req.format, data)

    chunks = chunk_pages(parsed.pages)
    if not chunks:
        raise ParserError("청크를 생성하지 못했다(추출 텍스트 없음).")

    # 임베딩 차원 검증은 EmbeddingProvider가 수행(EMBEDDING_DIM 불일치 시 에러).
    vectors = embedder.embed([c.content for c in chunks])

    records = [
        ChunkRecord(
            chunk_index=c.chunk_index,
            page_no=c.page_no,
            content=c.content,
            token_count=c.token_count,
            embedding=vec,
        )
        for c, vec in zip(chunks, vectors, strict=True)
    ]
    repo.replace_document_chunks(req.document_id, records)

    full_text = "\n".join(p.text for p in parsed.pages)
    return ParseResponse(
        chunk_count=len(records),
        language=detect_language(full_text),
        page_count=parsed.page_count,
    )
