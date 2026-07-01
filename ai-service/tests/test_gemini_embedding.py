"""GeminiEmbedding 테스트 — 페이크 client 주입으로 google-genai/네트워크 없이 검증."""

from __future__ import annotations

import pytest

from app.core.config import Settings
from app.providers import (
    EmbeddingProvider,
    GeminiEmbedding,
    ProviderConfigError,
    ProviderError,
    get_embedding_provider,
)


class _Emb:
    def __init__(self, values: list[float]) -> None:
        self.values = values


class _EmbResp:
    def __init__(self, embeddings: list[_Emb]) -> None:
        self.embeddings = embeddings


class _FakeEmbModels:
    """contents 당 값 하나 반환하는 페이크. return_dim/return_vec 로 형태 제어."""

    def __init__(
        self, *, return_dim: int | None = None, return_vec=None, short=False
    ):
        self.calls: list[dict] = []
        self._return_dim = return_dim
        self._return_vec = return_vec
        self._short = short

    def embed_content(self, *, model, contents, config):
        self.calls.append(
            {"model": model, "contents": list(contents), "config": dict(config)}
        )
        n = len(contents)
        if self._short:
            n -= 1  # 개수 불일치 유발
        if self._return_vec is not None:
            return _EmbResp([_Emb(list(self._return_vec)) for _ in range(n)])
        d = self._return_dim or config.get("output_dimensionality")
        return _EmbResp([_Emb([1.0] * d) for _ in range(n)])


class _FakeEmbClient:
    def __init__(self, models: _FakeEmbModels) -> None:
        self.models = models


def _embed(dim=1024, **kwargs) -> GeminiEmbedding:
    models = kwargs.pop("models", _FakeEmbModels())
    return GeminiEmbedding(
        api_key="AQ.k", model="gemini-embedding-001", dim=dim,
        client=_FakeEmbClient(models), **kwargs,
    )


# ── 해피패스 ──
def test_embed_returns_vectors_of_configured_dim():
    emb = _embed(dim=8, normalize=False)
    out = emb.embed(["a", "b", "c"])
    assert len(out) == 3
    assert all(len(v) == 8 for v in out)


def test_output_dimensionality_passed_in_config():
    models = _FakeEmbModels()
    emb = _embed(dim=1024, models=models, normalize=False)
    emb.embed(["x"])
    assert models.calls[0]["config"]["output_dimensionality"] == 1024


def test_empty_input_returns_empty_without_call():
    models = _FakeEmbModels()
    assert _embed(models=models).embed([]) == []
    assert models.calls == []


def test_normalize_produces_unit_vectors():
    emb = _embed(dim=2, normalize=True, models=_FakeEmbModels(return_vec=[3.0, 4.0]))
    (vec,) = emb.embed(["only"])
    assert vec == pytest.approx([0.6, 0.8])  # 3-4-5 → 0.6, 0.8


def test_implements_protocol():
    assert isinstance(_embed(), EmbeddingProvider)


# ── 배치 ──
def test_batches_over_100():
    models = _FakeEmbModels(return_dim=4)
    emb = _embed(dim=4, models=models, normalize=False)
    out = emb.embed([f"t{i}" for i in range(250)])
    assert len(out) == 250
    assert len(models.calls) == 3  # 100 + 100 + 50


# ── 실패케이스 ──
def test_missing_config_raises():
    c = _FakeEmbClient(_FakeEmbModels())
    with pytest.raises(ProviderConfigError):
        GeminiEmbedding(api_key="", model="m", dim=1024, client=c)
    with pytest.raises(ProviderConfigError):
        GeminiEmbedding(api_key="AQ.k", model="", dim=1024, client=c)
    with pytest.raises(ProviderConfigError):
        GeminiEmbedding(api_key="AQ.k", model="m", dim=0, client=c)


def test_dimension_mismatch_raises():
    # dim=1024 를 기대하지만 모델이 768 반환.
    emb = _embed(dim=1024, models=_FakeEmbModels(return_dim=768), normalize=False)
    with pytest.raises(ProviderError, match="차원 불일치"):
        emb.embed(["a"])


def test_count_mismatch_raises():
    models = _FakeEmbModels(return_dim=4, short=True)
    emb = _embed(dim=4, models=models, normalize=False)
    with pytest.raises(ProviderError, match="개수 불일치"):
        emb.embed(["a", "b"])


# ── 팩토리(환경변수 선택) ──
def test_factory_follows_llm_provider(monkeypatch):
    """embedding_provider 미설정 → llm_provider=gemini 면 GeminiEmbedding."""
    captured: dict = {}

    def fake_init(self, **kwargs):
        captured.update(kwargs)
        self._dim = kwargs["dim"]

    monkeypatch.setattr(GeminiEmbedding, "__init__", fake_init)
    cfg = Settings(
        llm_provider="gemini",
        llm_api_key="AQ.secret",
        embedding_model="gemini-embedding-001",
        embedding_dim=1024,
    )
    provider = get_embedding_provider(cfg)
    assert isinstance(provider, GeminiEmbedding)
    assert captured["model"] == "gemini-embedding-001"
    assert captured["dim"] == 1024
    # embedding_api_key 미설정 → llm_api_key 로 폴백.
    assert captured["api_key"] == "AQ.secret"


def test_factory_explicit_embedding_provider(monkeypatch):
    """embedding_provider=gemini 는 llm_provider 와 무관하게 선택."""
    monkeypatch.setattr(
        GeminiEmbedding, "__init__", lambda self, **kw: setattr(self, "_dim", kw["dim"])
    )
    cfg = Settings(
        llm_provider="commercial",
        embedding_provider="gemini",
        llm_api_key="AQ.secret",
        embedding_model="gemini-embedding-001",
    )
    assert isinstance(get_embedding_provider(cfg), GeminiEmbedding)
