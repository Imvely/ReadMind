"""언어 감지. langdetect는 비결정적이므로 시드를 고정한다."""

from __future__ import annotations

from langdetect import DetectorFactory, LangDetectException, detect

DetectorFactory.seed = 0


def detect_language(text: str) -> str | None:
    sample = text.strip()
    if not sample:
        return None
    try:
        return detect(sample)
    except LangDetectException:
        return None
