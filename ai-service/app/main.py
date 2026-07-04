"""ReadMind AI 서비스 진입점 (FastAPI). 내부 전용(명세서 §5)."""

from __future__ import annotations

from fastapi import FastAPI

from app.api.highlights import router as highlights_router
from app.api.parse import router as parse_router
from app.api.qa import router as qa_router
from app.api.summarize import router as summarize_router
from app.api.translate import router as translate_router

# 내부 전용 서비스 — 공개 HF Space에서 API 스키마가 노출되지 않도록 docs/openapi를 끈다.
# (/ai/*는 AI_SERVICE_TOKEN으로 보호되지만, 스키마 노출 자체를 차단. /health는 유지)
app = FastAPI(
    title="ReadMind AI Service",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)
app.include_router(parse_router)
app.include_router(summarize_router)
app.include_router(highlights_router)
app.include_router(qa_router)
app.include_router(translate_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
