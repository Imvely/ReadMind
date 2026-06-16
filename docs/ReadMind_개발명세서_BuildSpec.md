# ReadMind 개발 명세서 (Claude Code Build Spec)

> **이 문서의 목적**: Claude Code가 이 문서 **하나만 보고** ReadMind MVP → Phase 2까지 개발에 착수할 수 있도록, 아키텍처·데이터 모델·API 계약·디렉터리 구조·구현 순서를 빠짐없이 명세한다.
> **제품 한 줄 정의**: 논문/문서/전자책을 읽으면 AI가 자동으로 요약·복습카드·Q&A를 만들어주는 웹+안드로이드 동기화 학습 리더.
> **핵심 원칙**: 리더는 무료 입구, AI(이해)·기억(복습)은 유료. **읽기는 공짜, 이해와 기억은 프리미엄.**

---

## 0. Claude Code를 위한 작업 지침 (READ FIRST)

### 0.1 빌드 순서 (반드시 이 순서로)
이 명세서는 4개 모듈로 구성된다. **Phase 0(웹 MVP)을 먼저 완성하고 검증한 뒤** 다음으로 넘어간다. 앱 껍데기부터 만들지 않는다.

1. **M1 — AI 서비스 (Python/FastAPI)**: 문서 파싱 → 요약 → 하이라이트 추출 → 질문 생성 → RAG Q&A. (가치의 심장. 가장 먼저.)
2. **M2 — 백엔드 (Spring Boot)**: 인증·문서·하이라이트·동기화·결제 API. AI 서비스 호출 위임.
3. **M3 — 웹 프론트 (React/TS)**: 리더 + 대시보드 + AI 패널.
4. **M4 — 안드로이드 (React Native)**: M3 컴포넌트/로직 최대 재사용.

### 0.2 절대 규칙
- **시크릿은 코드에 하드코딩 금지.** 모두 환경변수(`.env`, Spring `application.yml` 프로파일)로 주입. `.env.example`만 커밋.
- **타입 우선.** TS strict, Python은 pydantic 모델로 모든 I/O를 검증. API 계약(§4)을 단일 진실원천(SSOT)으로 삼는다.
- **AI 응답은 항상 근거(source span)를 포함**한다. 환각 방지를 위해 RAG 답변은 원문 청크 인덱스를 같이 반환한다.
- **AI는 변동비**다. 무료 티어는 쿼터 제한, 임베딩·요약 결과는 캐싱(같은 문서 재처리 금지).
- 커밋은 작은 단위로, 각 모듈 완료 시 `README` 갱신.

### 0.3 기술 스택 고정값
| 영역 | 기술 | 버전 가이드 |
|---|---|---|
| AI 서비스 | Python 3.11+, FastAPI, uvicorn, pydantic v2 | — |
| 문서 파싱 | PyMuPDF(fitz), ebooklib(EPUB), python-docx, BeautifulSoup(HTML) | — |
| OCR | (옵션) 스캔 PDF용 OCR 엔진 추상화 인터페이스로 두고 후순위 |
| 임베딩/벡터 | pgvector(우선) 또는 Qdrant | — |
| LLM 접근 | `LLMProvider` 인터페이스로 추상화 (상용 API ↔ 자체 GPU 오픈소스 교체 가능) | — |
| 백엔드 | Spring Boot 3.x (Kotlin 권장), Spring Security, JPA | Java 21 / Kotlin |
| DB | PostgreSQL 16, Redis 7 | — |
| 파일 스토리지 | S3 호환(MinIO 로컬) | — |
| 웹 | React 18 + TypeScript, Vite, TailwindCSS, TanStack Query, Zustand | — |
| PDF 렌더(웹) | pdf.js | — |
| EPUB 렌더(웹) | ep.js (epubjs) | — |
| 안드로이드 | React Native 0.74+, react-native-pdf, react-native-mmkv | — |
| 인증 | JWT(access+refresh), OAuth2(Google/Apple 후순위) | — |
| 결제 | 토스페이먼츠 또는 아임포트(국내 구독), 인터페이스로 추상화 | — |

---

## 1. 시스템 아키텍처

```
┌─────────────┐      ┌─────────────┐
│  Web (React)│      │ Android (RN)│
└──────┬──────┘      └──────┬──────┘
       │   HTTPS / REST (JWT)│
       └─────────┬───────────┘
                 ▼
        ┌───────────────────┐
        │  Spring Boot API  │  인증·문서·하이라이트·동기화·결제·쿼터
        │   (게이트웨이)     │
        └───┬───────────┬───┘
            │           │ 내부 REST (서비스 토큰)
            ▼           ▼
   ┌────────────┐  ┌──────────────────────┐
   │ PostgreSQL │  │  AI Service (FastAPI) │ 파싱·OCR·요약·번역·RAG·카드
   │  + pgvector│  └─────┬──────────┬──────┘
   │  Redis     │        │          │
   │  S3(MinIO) │        ▼          ▼
   └────────────┘   ┌─────────┐  ┌──────────────┐
                    │벡터 검색 │  │ LLMProvider  │→ 상용 API / 자체 GPU
                    └─────────┘  └──────────────┘
```

**요청 흐름 예시 — "이 논문 요약해줘"**
1. 클라이언트 → `POST /api/v1/documents/{id}/summarize` (Spring)
2. Spring: 권한·쿼터(무료 한도) 확인 → 캐시 확인 → 없으면 AI 서비스 `POST /ai/summarize` 호출
3. AI 서비스: 문서 텍스트 로드 → 청킹 → LLM 요약 → 결과 반환
4. Spring: 결과 DB 캐싱 → 클라이언트 응답
5. 캐시 히트 시 2~4 생략.

---

## 2. 도메인 모델 (개념)

| 엔티티 | 설명 |
|---|---|
| **User** | 계정. 티어(FREE/PRO/STUDENT), 쿼터 상태 |
| **Document** | 업로드 문서. 포맷, 파일 위치, 파싱 상태, 페이지 수 |
| **DocumentChunk** | 파싱된 텍스트 청크 + 임베딩(RAG 단위) |
| **Highlight** | 하이라이트(텍스트 범위, 색상) + 선택적 메모 |
| **Note** | 페이지/문서 단위 자유 메모 |
| **Bookmark** | 북마크(위치) |
| **ReadingProgress** | 문서별 읽기 위치·진행률 |
| **Summary** | 캐시된 AI 요약(문서/섹션/페이지 범위별) |
| **QaSession / QaMessage** | 논문 Q&A 대화 + 근거 |
| **Flashcard** | 자동 생성 복습카드 + FSRS 상태 |
| **Subscription** | 구독 상태·결제 |
| **UsageQuota** | 사용자별 AI 사용량(무료 한도 관리) |
| **SyncRecord** | 동기화용 변경 로그(증분 동기화) |

---

## 3. 데이터베이스 스키마 (PostgreSQL)

> 모든 테이블 `id BIGSERIAL PK`, `created_at`, `updated_at TIMESTAMPTZ DEFAULT now()`. 소프트 삭제는 `deleted_at` 사용. 동기화를 위해 사용자 데이터 테이블에 `client_updated_at`, `version` 포함.

```sql
-- ============ 사용자/구독 ============
CREATE TABLE users (
  id              BIGSERIAL PRIMARY KEY,
  email           VARCHAR(255) UNIQUE NOT NULL,
  password_hash   VARCHAR(255),                 -- OAuth만이면 NULL 가능
  display_name    VARCHAR(100),
  tier            VARCHAR(20) NOT NULL DEFAULT 'FREE', -- FREE/PRO/STUDENT/TEAM
  school_verified BOOLEAN NOT NULL DEFAULT false,      -- STUDENT 인증 여부
  locale          VARCHAR(10) NOT NULL DEFAULT 'ko',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  plan          VARCHAR(20) NOT NULL,          -- PRO_MONTHLY/PRO_YEARLY/STUDENT
  status        VARCHAR(20) NOT NULL,          -- ACTIVE/CANCELED/PAST_DUE/TRIAL
  provider      VARCHAR(20) NOT NULL,          -- TOSS/IAMPORT
  provider_ref  VARCHAR(255),                  -- 결제사 빌링키/구독ID
  current_period_end TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_quotas (
  user_id       BIGINT PRIMARY KEY REFERENCES users(id),
  period_start  DATE NOT NULL,                 -- 월 단위 리셋
  ai_summary_used   INT NOT NULL DEFAULT 0,
  ai_qa_used        INT NOT NULL DEFAULT 0,
  ai_translate_used INT NOT NULL DEFAULT 0,
  storage_bytes     BIGINT NOT NULL DEFAULT 0,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ 문서 ============
CREATE TABLE documents (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  title         VARCHAR(500) NOT NULL,
  format        VARCHAR(20) NOT NULL,          -- PDF/EPUB/TXT/DOCX/HWP/MOBI/FB2/RTF/HTML/CBZ/MD
  storage_key   VARCHAR(500) NOT NULL,         -- S3 key
  file_size     BIGINT NOT NULL,
  page_count    INT,
  language      VARCHAR(10),
  parse_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/PARSING/READY/FAILED
  is_synced     BOOLEAN NOT NULL DEFAULT false,         -- 클라우드 동기화 대상(유료)
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_user ON documents(user_id) WHERE deleted_at IS NULL;

CREATE TABLE document_chunks (
  id            BIGSERIAL PRIMARY KEY,
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  chunk_index   INT NOT NULL,                  -- 문서 내 순서
  page_no       INT,                           -- 해당 청크의 페이지(있으면)
  content       TEXT NOT NULL,
  embedding     vector(1024),                  -- pgvector. 차원은 모델에 맞춤
  token_count   INT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(document_id, chunk_index)
);
CREATE INDEX idx_chunks_doc ON document_chunks(document_id);
-- 벡터 인덱스(데이터 적재 후): CREATE INDEX ON document_chunks USING hnsw (embedding vector_cosine_ops);

-- ============ 주석류(동기화 대상) ============
CREATE TABLE highlights (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  page_no       INT,
  -- 위치: PDF는 좌표 rects, EPUB은 CFI. 포맷 무관 저장 위해 JSON.
  location      JSONB NOT NULL,                -- { "type":"pdf", "page":3, "rects":[...] } | { "type":"epub","cfi":"..." }
  selected_text TEXT NOT NULL,
  color         VARCHAR(20) NOT NULL DEFAULT 'yellow',
  note          TEXT,                          -- 하이라이트에 달린 메모(선택)
  ai_suggested  BOOLEAN NOT NULL DEFAULT false,-- AI가 추천한 하이라이트인지
  tags          TEXT[],
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_highlights_user_doc ON highlights(user_id, document_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_highlights_search ON highlights USING gin (to_tsvector('simple', selected_text));

CREATE TABLE notes (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  page_no       INT,
  body          TEXT NOT NULL,
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bookmarks (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  location      JSONB NOT NULL,
  label         VARCHAR(200),
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reading_progress (
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  location      JSONB NOT NULL,                -- 마지막 읽은 위치
  percent       NUMERIC(5,2) NOT NULL DEFAULT 0,
  client_updated_at TIMESTAMPTZ,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, document_id)
);

-- ============ AI 산출물 ============
CREATE TABLE summaries (
  id            BIGSERIAL PRIMARY KEY,
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  scope         VARCHAR(20) NOT NULL,          -- DOCUMENT/SECTION/PAGE_RANGE
  scope_ref     JSONB,                         -- { "from":1,"to":5 } 등
  style         VARCHAR(20) NOT NULL DEFAULT 'PAPER', -- PAPER(목적/방법/결과/한계)/PLAIN
  content       JSONB NOT NULL,                -- 구조화 요약 결과
  model         VARCHAR(50),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(document_id, scope, scope_ref, style)
);

CREATE TABLE qa_sessions (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE qa_messages (
  id            BIGSERIAL PRIMARY KEY,
  session_id    BIGINT NOT NULL REFERENCES qa_sessions(id) ON DELETE CASCADE,
  role          VARCHAR(10) NOT NULL,          -- USER/ASSISTANT
  content       TEXT NOT NULL,
  sources       JSONB,                         -- [{chunk_index, page_no, snippet}] 근거
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE flashcards (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT REFERENCES documents(id) ON DELETE SET NULL,
  source_highlight_id BIGINT REFERENCES highlights(id) ON DELETE SET NULL,
  front         TEXT NOT NULL,
  back          TEXT NOT NULL,
  -- FSRS 상태
  fsrs_stability   NUMERIC,
  fsrs_difficulty  NUMERIC,
  due_at        TIMESTAMPTZ,
  last_review_at TIMESTAMPTZ,
  state         VARCHAR(20) NOT NULL DEFAULT 'NEW', -- NEW/LEARNING/REVIEW/RELEARNING
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_flashcards_due ON flashcards(user_id, due_at) WHERE deleted_at IS NULL;
```

> **포맷 무관 위치 저장 핵심**: `location JSONB`로 PDF(page+rects)와 EPUB(CFI)을 동일 컬럼에 담는다. 클라이언트 렌더러가 `type`을 보고 해석. 새 포맷 추가 시 스키마 변경 불필요.

---
*(이어서 §4 API 계약, §5 AI 서비스 상세, §6 프론트, §7 디렉터리, §8 구현 순서)*

## 4. API 계약 (Spring Boot — 클라이언트용)

> Base: `/api/v1`. 인증: `Authorization: Bearer <accessToken>`. 응답 공통 래퍼:
> `{ "success": true, "data": {...} }` 또는 `{ "success": false, "error": { "code": "...", "message": "..." } }`
> 페이지네이션: `?page=0&size=20`, 응답 `data.items[], data.totalElements, data.hasNext`.

### 4.1 인증
| 메서드 | 경로 | 설명 | 바디/응답 |
|---|---|---|---|
| POST | `/auth/signup` | 회원가입 | req `{email,password,displayName}` → `{userId}` |
| POST | `/auth/login` | 로그인 | req `{email,password}` → `{accessToken,refreshToken,user}` |
| POST | `/auth/refresh` | 토큰 갱신 | req `{refreshToken}` → `{accessToken}` |
| POST | `/auth/logout` | 로그아웃(리프레시 폐기) | — |
| GET | `/auth/me` | 내 정보 | → `{id,email,displayName,tier,quota}` |
| POST | `/auth/student-verify` | 학교 이메일 인증 | req `{schoolEmail}` → 인증메일 발송 |

### 4.2 문서
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/documents` | 업로드 초기화 → presigned URL 반환 `{documentId, uploadUrl}` |
| POST | `/documents/{id}/complete` | 업로드 완료 통지 → 파싱 비동기 시작 |
| GET | `/documents` | 내 문서 목록(검색·정렬·필터) |
| GET | `/documents/{id}` | 문서 메타 + parse_status |
| GET | `/documents/{id}/content` | 렌더용 원문 스트림/URL(권한 확인) |
| DELETE | `/documents/{id}` | 소프트 삭제 |

**업로드 플로우(중요)**: 클라이언트가 직접 S3에 presigned PUT → `complete` 호출 → Spring이 AI 서비스에 파싱 요청(비동기) → 파싱 완료 시 `documents.parse_status=READY`. 클라이언트는 폴링 또는 SSE `/documents/{id}/events`로 상태 수신.

### 4.3 주석/진행률 (동기화 대상)
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET/POST | `/documents/{id}/highlights` | 목록 / 생성 |
| PATCH/DELETE | `/highlights/{hid}` | 수정 / 삭제 |
| GET/POST | `/documents/{id}/notes` | 메모 |
| GET/POST | `/documents/{id}/bookmarks` | 북마크 |
| PUT | `/documents/{id}/progress` | 읽기 진행률 저장 |
| GET | `/highlights/search?q=&tag=` | **하이라이트 통합 검색(유료 핵심)** — 전 문서 횡단 |

**하이라이트 생성 req 예시**
```json
{ "pageNo": 3,
  "location": { "type":"pdf", "page":3, "rects":[{"x":..,"y":..,"w":..,"h":..}] },
  "selectedText": "domain adaptation improves...",
  "color": "yellow", "note": "내 연구와 연결", "tags": ["domain-adaptation"] }
```

### 4.4 AI 기능 (쿼터·권한 게이트 적용)
| 메서드 | 경로 | 티어 | 설명 |
|---|---|---|---|
| POST | `/documents/{id}/summarize` | FREE(소량)/PRO | `{scope:"DOCUMENT|SECTION|PAGE_RANGE", scopeRef, style:"PAPER|PLAIN"}` → 구조화 요약(캐시) |
| POST | `/documents/{id}/translate` | FREE(소량)/PRO | `{scope, text?, targetLang:"ko"}` → 번역(원문대조) |
| POST | `/documents/{id}/qa` | PRO | `{sessionId?, question}` → `{answer, sources:[{page,snippet}]}` |
| POST | `/documents/{id}/suggest-highlights` | PRO | AI 핵심 문장 추천 → 후보 하이라이트 배열 |
| POST | `/documents/{id}/flashcards/generate` | PRO | 하이라이트·요약 기반 카드 자동 생성 |
| GET | `/flashcards/due` | PRO | 오늘 복습할 카드(FSRS) |
| POST | `/flashcards/{fid}/review` | PRO | `{rating:"AGAIN|HARD|GOOD|EASY"}` → 다음 due 계산 |

**쿼터 게이트**: FREE가 한도 초과 시 `403 QUOTA_EXCEEDED` + 업그레이드 유도 메시지. Spring이 `usage_quotas`로 사전 차단(AI 서비스 호출 전).

### 4.5 동기화 (증분, 유료)
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/sync/changes?since=<ts>` | `since` 이후 변경된 highlights/notes/bookmarks/progress/flashcards 반환 |
| POST | `/sync/push` | 클라이언트 로컬 변경 배치 업로드. 충돌 시 **last-write-wins**(version 비교), 충돌 항목 반환 |

**동기화 정책**: 무료=로컬 전용(클라이언트 DB). 유료=클라우드 동기화 활성. 충돌 해결은 `client_updated_at` + `version` 기준 LWW, 삭제는 tombstone(`deleted_at`)로 전파.

### 4.6 결제
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/billing/checkout` | `{plan}` → 결제사 결제창 파라미터 |
| POST | `/billing/webhook` | 결제사 콜백(서명 검증) → 구독 상태 갱신 |
| GET | `/billing/subscription` | 내 구독 상태 |
| POST | `/billing/cancel` | 구독 해지 |

---

## 5. AI 서비스 명세 (Python / FastAPI) — M1, 최우선 구현

> 내부 전용. Spring에서 서비스 토큰으로 호출. 외부 노출 금지.
> 모든 LLM 호출은 `LLMProvider` 인터페이스 경유(상용 API ↔ 자체 GPU 교체 가능).

### 5.1 엔드포인트
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/ai/parse` | 문서 파싱: S3 key 받아 텍스트 추출·청킹·임베딩 → chunks 저장 |
| POST | `/ai/summarize` | 요약(스타일별 프롬프트) |
| POST | `/ai/translate` | 번역 |
| POST | `/ai/qa` | RAG 질의응답(근거 포함) |
| POST | `/ai/suggest-highlights` | 핵심 문장 추출 |
| POST | `/ai/flashcards` | 복습카드 생성 |
| GET | `/health` | 헬스체크 |

### 5.2 파싱 파이프라인 (`/ai/parse`)
```
입력: { documentId, storageKey, format }
1. S3에서 파일 다운로드
2. 포맷별 추출기 디스패치:
   - PDF  → PyMuPDF(fitz): 페이지별 텍스트(+ 스캔이면 OCR 큐로)
   - EPUB → ebooklib: 스파인 순회, HTML→텍스트
   - DOCX → python-docx
   - HWP/HWPX → hwp5/hwpx 파서(라이브러리 추상화, 후순위 가능)
   - TXT/MD/RTF/HTML → 직접/BeautifulSoup
3. 정규화 텍스트 → 청킹(약 500~800 토큰, 문단/문장 경계 우선, 오버랩 80토큰)
4. 각 청크 임베딩 생성(EmbeddingProvider)
5. document_chunks 저장(content, page_no, embedding)
6. 언어 감지 → documents.language, page_count 갱신, parse_status=READY
출력: { chunkCount, language, pageCount }
```

### 5.3 요약 (`/ai/summarize`)
- **PAPER 스타일** 출력 스키마(JSON 고정):
```json
{ "tldr": "...",
  "structure": { "objective":"연구 목적...", "method":"방법...", "results":"결과...",
                 "limitations":"한계...", "contribution":"기여..." },
  "keypoints": ["...", "..."],
  "glossary": [{"term":"domain adaptation","desc":"..."}] }
```
- **PLAIN 스타일**: `{ "tldr": "...", "keypoints": [...] }`
- 긴 문서는 map-reduce(청크별 요약 → 통합). 결과는 Spring이 `summaries`에 캐싱.

### 5.4 RAG Q&A (`/ai/qa`) — 환각 방지 필수
```
입력: { documentId, question, history? }
1. question 임베딩 → document_chunks에서 top-k(예 6) 코사인 검색
2. 검색 청크를 컨텍스트로 프롬프트 구성. "주어진 발췌문에만 근거해 답하라. 없으면 모른다고 답하라."
3. LLM 답변 + 인용 청크 매핑
출력: { "answer":"...", "sources":[{"chunkIndex":12,"pageNo":4,"snippet":"..."}] }
```
- **반드시 sources 반환**. 프론트는 근거 클릭 시 원문 위치로 점프.

### 5.5 핵심 문장 추출 / 복습카드
- `suggest-highlights`: 섹션별 핵심 문장 N개 추출 → `[{pageNo, text, reason}]`.
- `flashcards`: 하이라이트/요약 입력 → `[{front, back}]` Q-A쌍. front=개념질문, back=답.

### 5.6 FSRS (복습 스케줄) — Spring 또는 AI 서비스 중 한 곳에 구현
- 입력: 카드의 현재 stability/difficulty + rating(AGAIN/HARD/GOOD/EASY)
- 출력: 갱신된 stability/difficulty, 다음 `due_at`. (FSRS 알고리즘 라이브러리/공식 사용)

### 5.7 Provider 추상화 (교체 가능성 = 원가 우위)
```python
class LLMProvider(Protocol):
    def complete(self, system: str, user: str, *, json_mode: bool=False) -> str: ...
class EmbeddingProvider(Protocol):
    def embed(self, texts: list[str]) -> list[list[float]]: ...
# 구현체: CommercialLLM(api), SelfHostedLLM(자체 GPU/vLLM). 환경변수로 선택.
```

---

## 6. 프론트엔드 명세

### 6.1 웹 (React) — M3
**라우트**
- `/` 대시보드(내 서재: 문서 그리드, 최근 읽기, 복습 알림)
- `/reader/:docId` 리더(좌: 문서, 우: AI 패널 탭 = 요약/번역/Q&A/하이라이트)
- `/library` 문서 관리
- `/highlights` 하이라이트 통합 검색(유료)
- `/review` 복습카드(유료)
- `/settings`, `/billing`

**리더 화면 핵심 UX**
- PDF=pdf.js, EPUB=epubjs 렌더. 텍스트 선택 → 플로팅 툴바(하이라이트색/메모/AI에 질문).
- 우측 AI 패널: 탭 전환(요약/번역/Q&A). Q&A 답변의 `sources` 클릭 시 본문 해당 위치 하이라이트+스크롤.
- 리딩 설정: 다크/세피아/라이트, 폰트·크기·줄간격·여백, 단일/2단, 자동 스크롤.
- 무료 사용자가 AI 탭 클릭 → 쿼터 표시, 초과 시 업그레이드 모달.

**상태/데이터**: TanStack Query(서버 상태) + Zustand(리더 UI 상태). 낙관적 업데이트(하이라이트 즉시 표시 후 동기화).

### 6.2 안드로이드 (React Native) — M4
- 리더: react-native-pdf(PDF), epub은 WebView+epubjs 브리지.
- 오프라인 우선: 로컬 저장(MMKV/ SQLite). 무료=로컬만, 유료=`/sync/*`로 클라우드 동기화.
- 디바이스 최적화: 하단 시트 툴바, 제스처(페이지 넘김·밝기), 태블릿은 분할 화면.
- M3와 비즈니스 로직(API 클라이언트·타입·동기화 로직) 공유 패키지로 분리.

---

## 7. 모노레포 디렉터리 구조

```
readmind/
├── README.md
├── docker-compose.yml          # postgres(pgvector), redis, minio, ai-service, backend
├── .env.example
├── ai-service/                 # M1 (Python/FastAPI)
│   ├── app/
│   │   ├── main.py
│   │   ├── api/                # parse, summarize, translate, qa, flashcards 라우터
│   │   ├── core/               # config, security(service token)
│   │   ├── parsers/            # pdf.py, epub.py, docx.py, hwp.py, text.py (디스패처)
│   │   ├── chunking/           # splitter.py
│   │   ├── providers/          # llm.py, embedding.py (Protocol + 구현체)
│   │   ├── rag/                # retriever.py, qa.py
│   │   ├── summarize/          # paper.py, plain.py (프롬프트)
│   │   ├── fsrs/               # scheduler.py
│   │   └── schemas/            # pydantic I/O 모델
│   ├── tests/
│   ├── requirements.txt
│   └── Dockerfile
├── backend/                    # M2 (Spring Boot, Kotlin 권장)
│   ├── src/main/kotlin/com/readmind/
│   │   ├── auth/               # controller, service, jwt
│   │   ├── document/           # 업로드(presigned), 파싱 트리거, 조회
│   │   ├── annotation/         # highlight, note, bookmark, progress
│   │   ├── ai/                 # AI 서비스 클라이언트 + 쿼터 게이트 + 캐싱
│   │   ├── sync/               # 증분 동기화
│   │   ├── billing/            # 구독·웹훅
│   │   ├── quota/              # usage_quotas
│   │   └── common/             # 응답래퍼, 예외, 보안
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/       # Flyway: V1__init.sql (위 §3 스키마)
│   └── build.gradle.kts
├── web/                        # M3 (React/TS/Vite)
│   ├── src/
│   │   ├── routes/             # dashboard, reader, library, highlights, review, billing
│   │   ├── features/reader/    # pdf/epub 렌더, 선택 툴바, AI 패널
│   │   ├── features/ai/        # 요약/번역/QA 컴포넌트
│   │   ├── api/                # 생성된 타입 + fetch 클라이언트
│   │   ├── store/              # zustand
│   │   └── lib/
│   ├── index.html
│   └── package.json
├── mobile/                     # M4 (React Native)
│   └── src/ (web와 api/types/sync 공유)
└── packages/
    └── shared/                 # 공유 타입(API 계약), 동기화 로직, 상수
```

---

## 8. 구현 순서 (Claude Code 실행 체크리스트)

### Phase 0 — 웹 MVP (1개월 목표): "업로드 → 요약 → 핵심 하이라이트 → 질문 3개"
- [x] `docker-compose`로 postgres(pgvector)+redis+minio 기동, `.env.example` 작성
- [x] **AI 서비스**: `/ai/parse`(PDF만 먼저) + `/ai/summarize`(PAPER) + `/ai/suggest-highlights` + `/ai/qa`
- [x] `LLMProvider`/`EmbeddingProvider` 인터페이스 + 1개 구현체(환경변수로 모델 선택)
- [ ] **백엔드**: 회원/로그인(JWT), 문서 업로드(presigned), 파싱 트리거, `/summarize` `/qa` 위임 + 캐싱
- [ ] **웹**: 업로드 화면 + pdf.js 뷰어 + 우측 요약/Q&A 패널. Q&A sources 클릭 점프
- [ ] 대학원 커뮤니티 베타 배포. **검증 지표: 재사용(같은 사람이 다른 논문을 또 올리는가)**

### Phase 1 — 리더 + 동기화 (2~3개월)
- [ ] 하이라이트/메모/북마크/진행률 CRUD + 통합 검색 `/highlights/search`
- [ ] EPUB/TXT/DOCX 파서 추가, 리딩 설정(테마·폰트·자동스크롤)
- [ ] 번역 기능, 자동 하이라이트 추천 UI
- [ ] 안드로이드 앱(리더+오프라인) + `/sync/*` 증분 동기화(유료 게이트)

### Phase 2 — 기억 + 결제 (4~6개월)
- [ ] `/flashcards/generate` + FSRS 복습 큐 + `/review` 화면
- [ ] 쿼터 시스템(`usage_quotas`) + FREE/PRO/STUDENT 게이트 전면 적용
- [ ] 결제 연동(토스/아임포트) + 웹훅 + 구독 상태 관리
- [ ] 학생 이메일 인증, 업그레이드 모달, 전환율 계측

### Phase 3 — 확장 (7~12개월)
- [ ] HWP/HWPX, MOBI/FB2/CBZ 포맷, OCR(스캔 PDF)
- [ ] 팀/연구실 공유 컬렉션, 협업 하이라이트
- [ ] 인접 타깃(수험·직장인) 온보딩 분기

---

## 9. 비기능 요구사항
- **보안**: 문서는 사용자 격리(행 수준 권한 체크 필수). presigned URL 단기 만료. 서비스 간 토큰 인증.
- **개인정보**: 문서 본문은 사용자 자산. AI 처리 로그에 원문 장기 보관 금지(옵트아웃 제공).
- **성능**: 요약/임베딩 캐싱으로 동일 문서 재처리 0회. 벡터 검색 hnsw 인덱스.
- **비용 관측**: AI 호출당 토큰·원가 로깅 → 사용자당 월 AI 원가 대시보드(단위경제 추적).
- **확장성**: AI 서비스 수평 확장(상태 없음). 무거운 파싱은 큐(Redis/RQ 등) 비동기.

## 10. 환경변수 (`.env.example` 핵심 키)
```
# DB / infra
POSTGRES_URL=...        REDIS_URL=...        S3_ENDPOINT=...  S3_BUCKET=...  S3_KEY=...  S3_SECRET=...
# auth
JWT_SECRET=...          JWT_ACCESS_TTL=900   JWT_REFRESH_TTL=1209600
# service-to-service
AI_SERVICE_URL=http://ai-service:8000   AI_SERVICE_TOKEN=...
# LLM (provider 추상화 → 교체 가능)
LLM_PROVIDER=commercial|selfhosted      LLM_MODEL=...        LLM_API_BASE=...   LLM_API_KEY=...
EMBEDDING_MODEL=...     EMBEDDING_DIM=1024
# billing
BILLING_PROVIDER=toss|iamport           BILLING_API_KEY=...  BILLING_WEBHOOK_SECRET=...
# quota (free tier 한도)
FREE_SUMMARY_PER_MONTH=10   FREE_QA_PER_MONTH=20   FREE_TRANSLATE_PER_MONTH=10   FREE_STORAGE_MB=200
```

---
**끝.** 이 문서는 §3 스키마와 §4/§5 API 계약을 단일 진실원천으로 삼는다. 구현 중 충돌 시 이 문서를 먼저 갱신하고 코드를 맞춘다.
