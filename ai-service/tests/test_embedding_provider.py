"""EmbeddingProvider 테스트 — 차원 검증(EMBEDDING_DIM)이 핵심."""

from __future__ import annotations

import httpx
import pytest

from app.core.config import Settings
from app.providers import (
    EmbeddingProvider,
    OpenAICompatEmbedding,
    ProviderConfigError,
    ProviderError,
    get_embedding_provider,
)


def _client(handler) -> httpx.Client:
    return httpx.Client(
        transport=httpx.MockTransport(handler), base_url="http://test.local"
    )


def _embed_handler(dim: int, count: int):
    def handler(request: httpx.Request) -> httpx.Response:
        data = [
            {"index": i, "embedding": [0.1] * dim} for i in range(count)
        ]
        return httpx.Response(200, json={"data": data})

    return handler


# ── 해피패스 ──
def test_embed_returns_vectors():
    emb = OpenAICompatEmbedding(
        api_base="http://x",
        model="m",
        dim=1024,
        client=_client(_embed_handler(1024, 2)),
    )
    vecs = emb.embed(["a", "b"])
    assert len(vecs) == 2
    assert all(len(v) == 1024 for v in vecs)
    assert isinstance(emb, EmbeddingProvider)


def test_embed_empty_input_short_circuits():
    emb = OpenAICompatEmbedding(
        api_base="http://x",
        model="m",
        dim=1024,
        client=_client(_embed_handler(1024, 0)),
    )
    assert emb.embed([]) == []


def test_embed_sorts_by_index():
    def handler(request: httpx.Request) -> httpx.Response:
        # 일부러 역순으로 반환 → index 기준 정렬 확인.
        return httpx.Response(
            200,
            json={
                "data": [
                    {"index": 1, "embedding": [2.0, 2.0]},
                    {"index": 0, "embedding": [1.0, 1.0]},
                ]
            },
        )

    emb = OpenAICompatEmbedding(
        api_base="http://x", model="m", dim=2, client=_client(handler)
    )
    vecs = emb.embed(["first", "second"])
    assert vecs[0] == [1.0, 1.0]
    assert vecs[1] == [2.0, 2.0]


# ── 실패케이스 ──
def test_dim_mismatch_raises():
    emb = OpenAICompatEmbedding(
        api_base="http://x", model="m", dim=1024, client=_client(_embed_handler(512, 1))
    )
    with pytest.raises(ProviderError, match="차원 불일치"):
        emb.embed(["a"])


def test_count_mismatch_raises():
    emb = OpenAICompatEmbedding(
        api_base="http://x", model="m", dim=4, client=_client(_embed_handler(4, 1))
    )
    with pytest.raises(ProviderError, match="개수 불일치"):
        emb.embed(["a", "b"])


def test_missing_model_raises():
    with pytest.raises(ProviderConfigError):
        OpenAICompatEmbedding(api_base="http://x", model="", dim=1024)


def test_bad_dim_raises():
    with pytest.raises(ProviderConfigError):
        OpenAICompatEmbedding(api_base="http://x", model="m", dim=0)


# ── 팩토리 + 차원 기본값 ──
def test_factory_uses_default_dim_1024():
    cfg = Settings(
        llm_api_base="http://api/v1",
        llm_api_key="k",
        embedding_model="emb",
    )
    # EMBEDDING_DIM 기본값 1024, embedding api_base/key는 llm 값 재사용.
    assert cfg.embedding_dim == 1024
    provider = get_embedding_provider(cfg)
    assert isinstance(provider, OpenAICompatEmbedding)
