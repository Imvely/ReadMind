"""PgChunkRepository 실 DB 통합 테스트 (pgvector).

명세서 §3 스키마를 테스트용으로 만들어 실제 INSERT/벡터 적재를 검증한다.
DDL은 본래 백엔드 Flyway 소유이므로 여기서는 테스트 셋업으로만 생성하고 정리한다.
Postgres에 접속 불가하면 skip.
"""

from __future__ import annotations

import os

import pytest

psycopg = pytest.importorskip("psycopg")

from app.parse.ports import ChunkRecord  # noqa: E402
from app.repositories.chunks_pg import PgChunkRepository  # noqa: E402

DSN = os.environ.get(
    "READMIND_DATABASE_URL",
    "postgresql://readmind:readmind@localhost:5432/readmind",
)

# 격리용 스키마. public은 M2 Flyway 소유이므로 건드리지 않는다.
_SCHEMA = "test_parse"
# 리포는 unqualified `document_chunks`를 쓰므로 search_path를 스키마로 고정.
# vector 타입/함수는 public(확장 설치 위치)에 있으므로 public도 경로에 포함한다.
_REPO_DSN = (
    DSN
    + ("&" if "?" in DSN else "?")
    + "options=-c%20search_path%3D" + _SCHEMA + "%2Cpublic"
)

_SETUP = f"""
CREATE EXTENSION IF NOT EXISTS vector;
DROP SCHEMA IF EXISTS {_SCHEMA} CASCADE;
CREATE SCHEMA {_SCHEMA};
SET search_path TO {_SCHEMA}, public;
CREATE TABLE documents (id BIGSERIAL PRIMARY KEY);
CREATE TABLE document_chunks (
  id          BIGSERIAL PRIMARY KEY,
  document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  chunk_index INT NOT NULL,
  page_no     INT,
  content     TEXT NOT NULL,
  embedding   vector(1024),
  token_count INT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(document_id, chunk_index)
);
"""


@pytest.fixture
def db():
    try:
        conn = psycopg.connect(DSN, connect_timeout=3)
    except Exception as exc:  # 미기동/접속불가
        pytest.skip(f"Postgres 접속 불가: {exc!r}")
    conn.autocommit = True
    with conn.cursor() as cur:
        cur.execute(_SETUP)
        cur.execute("INSERT INTO documents DEFAULT VALUES RETURNING id")
        doc_id = cur.fetchone()[0]
    try:
        yield conn, doc_id
    finally:
        with conn.cursor() as cur:
            cur.execute(f"DROP SCHEMA IF EXISTS {_SCHEMA} CASCADE")
        conn.close()


def _records(n: int) -> list[ChunkRecord]:
    return [
        ChunkRecord(
            chunk_index=i,
            page_no=i + 1,
            content=f"chunk {i}",
            token_count=3,
            embedding=[0.001 * i] * 1024,
        )
        for i in range(n)
    ]


def test_insert_and_query(db):
    conn, doc_id = db
    repo = PgChunkRepository(_REPO_DSN)
    repo.replace_document_chunks(doc_id, _records(3))

    with conn.cursor() as cur:
        cur.execute(
            "SELECT chunk_index, page_no, content FROM document_chunks "
            "WHERE document_id = %s ORDER BY chunk_index",
            (doc_id,),
        )
        rows = cur.fetchall()
    assert [r[0] for r in rows] == [0, 1, 2]
    assert [r[1] for r in rows] == [1, 2, 3]
    assert rows[0][2] == "chunk 0"


def test_replace_is_idempotent(db):
    conn, doc_id = db
    repo = PgChunkRepository(_REPO_DSN)
    repo.replace_document_chunks(doc_id, _records(5))
    repo.replace_document_chunks(doc_id, _records(2))  # 재파싱 → 덮어쓰기

    with conn.cursor() as cur:
        cur.execute(
            "SELECT count(*) FROM document_chunks WHERE document_id = %s",
            (doc_id,),
        )
        count = cur.fetchone()[0]
    assert count == 2


def test_embedding_roundtrip_dim(db):
    conn, doc_id = db
    repo = PgChunkRepository(_REPO_DSN)
    repo.replace_document_chunks(doc_id, _records(1))
    with conn.cursor() as cur:
        cur.execute(
            "SELECT vector_dims(embedding) FROM document_chunks "
            "WHERE document_id = %s",
            (doc_id,),
        )
        assert cur.fetchone()[0] == 1024
