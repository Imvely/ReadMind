"""document_chunks Postgres 저장소(psycopg3 + pgvector).

명세서 §3 스키마에 INSERT한다. 테이블 생성(DDL)은 백엔드 Flyway 소유이며 여기서
만들지 않는다 — 이 어댑터는 기존 테이블에 청크를 멱등하게 적재한다.
재파싱 시 같은 문서 청크를 재처리하지 않도록 기존 행을 지우고 새로 넣는다.
"""

from __future__ import annotations

import psycopg
from pgvector.psycopg import register_vector

from app.parse.ports import ChunkRecord


class PgChunkRepository:
    def __init__(self, dsn: str) -> None:
        self._dsn = dsn

    def replace_document_chunks(
        self, document_id: int, chunks: list[ChunkRecord]
    ) -> None:
        with psycopg.connect(self._dsn) as conn:
            register_vector(conn)
            with conn.cursor() as cur:
                cur.execute(
                    "DELETE FROM document_chunks WHERE document_id = %s",
                    (document_id,),
                )
                if chunks:
                    cur.executemany(
                        """
                        INSERT INTO document_chunks
                            (document_id, chunk_index, page_no, content,
                             embedding, token_count)
                        VALUES (%s, %s, %s, %s, %s, %s)
                        """,
                        [
                            (
                                document_id,
                                c.chunk_index,
                                c.page_no,
                                c.content,
                                c.embedding,
                                c.token_count,
                            )
                            for c in chunks
                        ],
                    )
            conn.commit()
