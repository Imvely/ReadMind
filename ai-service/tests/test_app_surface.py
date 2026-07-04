"""앱 노출 표면 테스트 — 내부 전용 서비스의 스키마 비공개 보장 (명세서 §5, §9 보안)."""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import app


def test_health_stays_public() -> None:
    resp = TestClient(app).get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_api_schema_endpoints_are_disabled() -> None:
    """공개 HF Space에서 /docs·/redoc·/openapi.json이 노출되면 안 된다."""
    client = TestClient(app)
    for path in ("/docs", "/redoc", "/openapi.json"):
        assert client.get(path).status_code == 404, f"{path} 가 노출되어 있음"
