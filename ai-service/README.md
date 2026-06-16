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

- **/ai/parse (§5.2)** — `app/parsers/`, `app/chunking/`, `app/parse/`, `app/api/`
  - 입력 `{documentId, storageKey, format}` → 출력 `{chunkCount, language, pageCount}`.
  - 파이프라인: S3 다운로드 → 포맷 디스패치(현재 **PDF**=PyMuPDF) → 청킹(500~800
    토큰, 문단/문장 경계 우선, 오버랩 80, `page_no` 보존) → `EmbeddingProvider`
    임베딩 → `document_chunks` 저장.
  - 포트(`Storage`/`ChunkRepository`)로 인프라를 분리 — 파이프라인은 페이크로
    테스트, 구현체는 `S3Storage`(boto3)/`PgChunkRepository`(psycopg+pgvector).
  - 청크 `page_no`는 기여 토큰이 가장 많은 페이지(근거 위치 추적용).
  - 내부 전용: `AI_SERVICE_TOKEN` 설정 시 `X-Service-Token` 헤더 강제(§5).

- **/ai/summarize (§5.3)** — `app/summarize/`, `app/schemas/summarize.py`
  - 입력 `{documentId, style}` (style: `PAPER`|`PLAIN`, 기본 PAPER).
  - `document_chunks`를 읽어 요약(재파싱 0회). 짧으면 단일 LLM 호출, 길면
    **map-reduce**(청크 그룹별 부분요약 → 통합).
  - PAPER 고정 JSON: `{tldr, structure{objective,method,results,limitations,
    contribution}, keypoints[], glossary[{term,desc}]}`. PLAIN: `{tldr, keypoints[]}`.
  - LLM 출력은 pydantic 스키마로 **검증**(누락/형식오류 → 422). 코드펜스/잡텍스트
    내성 파서 포함. LLM은 `LLMProvider.complete(json_mode=True)` 경유.
  - 소유권(user_id)·쿼터 검증은 백엔드 게이트(be-ai-gate-cache) 담당 — 내부 전용.

> 하이라이트/Q&A 라우터와 EPUB/DOCX 파서는 다음 항목에서 추가한다.
> `document_chunks` DDL은 백엔드 Flyway(M2) 소유 — AI 서비스는 적재/조회만 한다.

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
