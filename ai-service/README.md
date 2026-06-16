# ai-service (M1)

ReadMind AI 서비스. 문서 파싱 → 요약/하이라이트/Q&A를 담당한다(명세서 §5).

## 현재 구현 범위

- **Provider 추상화 (§5.7)** — `app/providers/`
  - `LLMProvider` / `EmbeddingProvider` Protocol
  - `OpenAICompatLLM` / `OpenAICompatEmbedding`: OpenAI 호환 HTTP 구현체 1개로
    상용 API와 자체 호스팅(vLLM)을 모두 커버. 벤더를 코드에 직접 묶지 않는다
    (CLAUDE.md §10) — 교체는 `.env`의 `LLM_API_BASE` / `LLM_MODEL` 변경만으로.
  - 임베딩 반환 차원은 `EMBEDDING_DIM`(기본 1024, `document_chunks.embedding
    vector(1024)`와 일치)과 다르면 즉시 에러.

> 파서/청킹/RAG/요약 라우터는 다음 항목(`ai-parse-pdf` 등)에서 추가한다.

## 환경변수 (명세서 §10)

| 키 | 설명 |
|---|---|
| `LLM_PROVIDER` | `commercial` \| `selfhosted` (현재 둘 다 OpenAI 호환 구현 사용) |
| `LLM_API_BASE` | 예: 상용 게이트웨이 `/v1`, 자체 vLLM `http://vllm:8000/v1` |
| `LLM_MODEL` | 모델 이름 |
| `LLM_API_KEY` | API 키(자체 호스팅은 비워도 됨) |
| `EMBEDDING_MODEL` | 임베딩 모델 |
| `EMBEDDING_DIM` | 기본 1024 |

`EMBEDDING_API_BASE` / `EMBEDDING_API_KEY` 미설정 시 LLM 값을 재사용한다.

## 개발

```bash
# venv (Python 3.11+)
py -3.14 -m venv .venv
.venv/Scripts/python -m pip install -r requirements-dev.txt

# 린트 + 테스트
.venv/Scripts/python -m ruff check .
.venv/Scripts/python -m pytest -q
```

테스트는 `httpx.MockTransport`로 실제 LLM 없이 provider 동작(해피패스/HTTP오류/
재시도/차원검증)을 검증한다.
