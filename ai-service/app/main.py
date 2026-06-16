"""ReadMind AI 서비스 진입점 (FastAPI). 내부 전용(명세서 §5)."""

from __future__ import annotations

from fastapi import FastAPI

from app.api.parse import router as parse_router

app = FastAPI(title="ReadMind AI Service", version="0.1.0")
app.include_router(parse_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
