"""PgChunkRepository.search_similar_chunks 실 DB 통합 테스트(pgvector 코사인).

실제 벡터 검색 SQL(`<=>`)과 거리 정렬을 검증한다. Postgres 미접속 시 skip.
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
_SCHEMA = "test_rag"
_REPO_DSN = (
    DSN
    + ("&" if "?" in DSN else "?")
    + "options=-c%20search_path%3D" + _SCHEMA + "%2Cpublic"
)
_DIM = 1024

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
  embedding   vector({_DIM}),
  token_count INT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(document_id, chunk_index)
);
"""


def _unit(i: int) -> list[float]:
    v = [0.0] * _DIM
    v[i] = 1.0
    return v


@pytest.fixture
def doc_id():
    try:
        conn = psycopg.connect(DSN, connect_timeout=3)
    except Exception as exc:
        pytest.skip(f"Postgres 접속 불가: {exc!r}")
    conn.autocommit = True
    with conn.cursor() as cur:
        cur.execute(_SETUP)
        cur.execute("INSERT INTO documents DEFAULT VALUES RETURNING id")
        did = cur.fetchone()[0]
    try:
        yield did
    finally:
        with conn.cursor() as cur:
            cur.execute(f"DROP SCHEMA IF EXISTS {_SCHEMA} CASCADE")
        conn.close()


def _seed(doc: int) -> PgChunkRepository:
    repo = PgChunkRepository(_REPO_DSN)
    repo.replace_document_chunks(
        doc,
        [
            ChunkRecord(0, 1, "chunk zero", 2, _unit(0)),
            ChunkRecord(1, 2, "chunk one", 2, _unit(1)),
            ChunkRecord(2, 3, "chunk two", 2, _unit(2)),
        ],
    )
    return repo


def test_search_orders_by_cosine_distance(doc_id):
    repo = _seed(doc_id)
    # 쿼리 = e1 → chunk_index 1이 가장 가깝다(거리 0).
    hits = repo.search_similar_chunks(doc_id, _unit(1), k=3)
    assert [h.chunk_index for h in hits][0] == 1
    assert hits[0].page_no == 2
    assert hits[0].distance == pytest.approx(0.0, abs=1e-6)
    # 거리 오름차순.
    assert hits == sorted(hits, key=lambda h: h.distance)


def test_search_respects_k(doc_id):
    repo = _seed(doc_id)
    assert len(repo.search_similar_chunks(doc_id, _unit(0), k=2)) == 2


def test_search_scoped_to_document(doc_id):
    repo = _seed(doc_id)
    # 다른(존재하지 않는) 문서 → 결과 없음.
    assert repo.search_similar_chunks(doc_id + 9999, _unit(0), k=3) == []
