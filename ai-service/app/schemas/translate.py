"""/ai/translate I/O 스키마 (명세서 §5, §4.4).

원문대조 번역: 입력 텍스트를 targetLang 으로 번역하고, 원문(sourceExcerpt)을 함께 돌려
클라이언트가 원문·번역을 나란히 보여줄 수 있게 한다.
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class TranslateRequest(BaseModel):
    text: str = Field(..., min_length=1)
    target_lang: str = Field("ko", alias="targetLang")

    model_config = {"populate_by_name": True}


class TranslateResponse(BaseModel):
    translated: str
    source_excerpt: str = Field(..., alias="sourceExcerpt")
    target_lang: str = Field(..., alias="targetLang")

    model_config = {"populate_by_name": True}
