# CLAUDE.md — ReadMind (가제)

> 이 파일은 Claude Code가 **매 작업마다 자동으로 읽는 상시 규칙**이다.
> "무엇을 만드는가"는 `docs/ReadMind_개발명세서_BuildSpec.md`(이하 **명세서**)가 단일 진실원천(SSOT).
> 이 파일은 "어떻게 작업하는가"의 규칙만 담는다. 충돌 시 명세서 §3 스키마·§4/§5 API 계약이 우선한다.
> ※ ReadMind는 가제다. 최종 서비스명이 정해지면 이 파일과 명세서의 이름만 일괄 교체한다(코드 상수는 `APP_NAME` 한 곳에서 관리).

---

## 1. 제품 한 줄 / 절대 원칙

**ReadMind**: 논문·문서·전자책을 읽으면 AI가 자동으로 요약·복습카드·Q&A를 만들어주는 웹+안드로이드 동기화 학습 리더.

핵심 철학 — 이 줄을 어기는 구현은 거부한다:
- **읽기는 무료, 이해(AI)와 기억(복습)은 유료.** 리더 기능을 유료 뒤에 가두지 않는다.
- **리더로 경쟁하지 않는다.** 차별화는 AI 학습/기억 레이어(RAG Q&A·요약·번역·하이라이트 검색·자동 복습카드)에만 있다.
- **기능 백화점 금지.** 명세서 Phase에 없는 기능을 임의로 추가하지 않는다. 기능 5개를 완벽히.

---

## 2. 빌드 순서 (반드시 준수)

명세서 §8 Phase를 **순서대로** 진행한다. 앞 Phase가 동작·검증되기 전에 다음으로 넘어가지 않는다.

1. **M1 AI 서비스(Python/FastAPI)** → 2. **M2 백엔드(Spring Boot)** → 3. **M3 웹(React)** → 4. **M4 안드로이드(RN)**
- **Phase 0(웹 MVP)**: "업로드 → 요약 → 핵심 하이라이트 → 질문 3개"만. 회원/리더 UI 최소화.
- 앱 껍데기·세팅부터 부풀리지 않는다. 가치(AI) 경로를 먼저 end-to-end로 잇는다.

> 새 작업을 시작하면, 먼저 명세서 §8 체크리스트에서 현재 위치를 확인하고 그 범위 안에서만 작업한다.

---

## 3. 절대 규칙 (위반 금지)

- **시크릿 하드코딩 금지.** 모든 키·토큰·DB 접속정보는 환경변수로 주입. `.env.example`만 커밋, `.env`는 `.gitignore`.
- **AI 응답에는 항상 근거(sources)를 포함.** RAG Q&A는 원문 청크 인덱스/페이지를 반드시 반환(명세서 §5.4). 근거 없는 답변 구현 금지.
- **AI는 변동비다.** 무료 티어는 쿼터로 사전 차단(AI 서비스 호출 전 Spring에서 막는다). 임베딩·요약 결과는 캐싱 — 같은 문서를 재처리하지 않는다.
- **사용자 데이터 격리.** 모든 문서/하이라이트 조회·수정은 행 수준 소유권 검증(`user_id` 일치) 후 수행. 누락 시 보안 결함으로 간주.
- **포맷 무관 위치 저장.** 하이라이트/북마크 위치는 `location JSONB`(PDF=page+rects, EPUB=cfi). 새 포맷 때문에 스키마를 바꾸지 않는다.
- **API 계약을 깨지 않는다.** 명세서 §4/§5의 경로·요청/응답 형태를 임의 변경 금지. 변경이 필요하면 **명세서를 먼저 고치고** 코드를 맞춘 뒤, 그 사실을 응답에 명시한다.
- **`packages/shared`의 타입이 SSOT.** 웹·안드·백엔드가 같은 API 타입을 공유한다. 중복 정의 금지.

---

## 4. 기술 스택 (고정값 — 임의 교체 금지)

| 영역 | 스택 |
|---|---|
| AI 서비스 | Python 3.11+, FastAPI, pydantic v2, PyMuPDF/ebooklib/python-docx, pgvector |
| 백엔드 | Spring Boot 3.x (Kotlin), Spring Security, JPA, Flyway |
| DB/인프라 | PostgreSQL 16 + pgvector, Redis 7, S3 호환(MinIO 로컬) |
| 웹 | React 18 + TypeScript(strict), Vite, Tailwind, TanStack Query, Zustand, pdf.js, epubjs |
| 안드로이드 | React Native 0.74+, react-native-pdf, MMKV/SQLite |
| 인증/결제 | JWT(access+refresh) / 토스·아임포트(추상화) |

- LLM/임베딩은 **`LLMProvider`/`EmbeddingProvider` 인터페이스 경유**(명세서 §5.7). 상용 API ↔ 자체 GPU 교체 가능해야 한다. 특정 벤더를 코드에 직접 묶지 않는다.

---

## 5. 코딩 규약

### 공통
- 타입 우선. TS `strict: true`, Python은 모든 I/O를 pydantic 모델로 검증.
- 의도가 드러나는 이름. 주석은 "왜"를 설명. 매직 넘버 금지 → 명명 상수/환경변수.
- 에러는 삼키지 않는다. 사용자에게는 명세서 §4의 에러 코드(`QUOTA_EXCEEDED` 등)로 일관 응답.

### Python (AI 서비스)
- 디렉터리: `parsers/`(포맷별 디스패처), `chunking/`, `providers/`, `rag/`, `summarize/`, `fsrs/`, `schemas/`.
- 포맷 파서는 디스패처 패턴(`format → parser`). 새 포맷은 파서 추가만으로 확장.
- 외부 호출(LLM/임베딩/S3)은 재시도·타임아웃. 무거운 작업은 큐로 비동기.
- 요약 PAPER 스타일은 명세서 §5.3의 JSON 스키마 고정 출력.

### Kotlin (백엔드)
- 레이어: controller → service → repository. 컨트롤러에 비즈니스 로직 금지.
- 응답은 공통 래퍼 `{success, data}` / `{success, error}`.
- 마이그레이션은 Flyway `V*__*.sql`. 스키마 직접 수정 금지.
- AI 호출은 `ai/` 모듈에 격리(쿼터 게이트 → 캐시 조회 → AI 서비스 호출 → 캐시 저장 순서 강제).

### TypeScript (웹/안드)
- 서버 상태=TanStack Query, UI 상태=Zustand. `useEffect`로 서버 패칭 직접 금지.
- 하이라이트 등은 낙관적 업데이트 후 동기화, 실패 시 롤백.
- 리더 렌더러(pdf/epub)는 어댑터로 분리해 포맷 교체 가능하게.

---

## 6. 동기화 규칙 (명세서 §4.5)

- 무료 = 로컬 전용. 유료 = 클라우드 동기화 활성.
- 증분 동기화: `GET /sync/changes?since=`, `POST /sync/push`.
- 충돌 해결 = last-write-wins(`version` + `client_updated_at`). 삭제는 tombstone(`deleted_at`)로 전파.
- 동기화 로직은 `packages/shared`에 두어 웹·안드가 공유.

---

## 7. 테스트 / 검증

- 새 엔드포인트에는 최소 1개의 해피패스 + 1개의 권한/쿼터 실패 테스트.
- AI 파이프라인: 샘플 PDF로 parse→summarize→qa가 **근거를 포함해** 끝까지 도는 통합 테스트 1개 유지.
- 커밋 전: 빌드 + 린트 + 타입 체크 통과. 깨진 채로 커밋하지 않는다.
- Phase 0 검증 지표는 **재사용률**(같은 사용자가 다른 논문을 또 올리는가). 기능 완성보다 이 지표 우선.

---

## 8. 작업 흐름 (매 작업 시)

1. 명세서 §8에서 현재 Phase·체크리스트 항목 확인 → 범위 확정.
2. 변경할 데이터 모델/계약이 있으면 **명세서 먼저 수정**.
3. 작은 단위로 구현 → 테스트 → 커밋. 한 번에 거대한 변경 금지.
4. 모듈 완료 시 해당 `README` 갱신, 명세서 §8 체크박스 갱신.
5. 보안(소유권 검증)·쿼터·캐싱·근거(sources) 4가지를 항상 자가 점검.

---

## 9. 커밋 / 브랜치

- 커밋 메시지: `feat(ai): /ai/summarize PAPER 스타일 구현` 처럼 `타입(스코프): 내용`.
- 타입: `feat|fix|refactor|test|docs|chore`.
- 브랜치: `phase0/...`, `feat/...`. main 직접 푸시 금지.

---

## 10. 금지 사항 (Do NOT)

- ❌ 리더 핵심 기능을 유료로 가두기
- ❌ 명세서에 없는 기능 임의 추가 / Phase 건너뛰기
- ❌ 시크릿 하드코딩 / `.env` 커밋
- ❌ 근거 없는 AI 답변 / 캐시 없이 같은 문서 재처리
- ❌ 소유권 검증 없는 데이터 접근
- ❌ 특정 LLM 벤더를 코드에 직접 결합
- ❌ API 계약을 코드에서만 몰래 변경(명세서 미반영)
- ❌ 깨진 빌드/타입 에러 상태로 커밋

---

## 11. 프로젝트 구조 (생성 후 이 형태가 되어야 함)

```
readmind/
├── CLAUDE.md                       ← 이 파일 (루트, 자동 로드)
├── README.md
├── docker-compose.yml              ← postgres(pgvector)/redis/minio/ai-service/backend
├── .env.example                    ← .env는 .gitignore
├── docs/
│   └── ReadMind_개발명세서_BuildSpec.md   ← 명세서 (SSOT)
├── ai-service/                     ← M1 Python/FastAPI
├── backend/                        ← M2 Spring Boot(Kotlin)
├── web/                            ← M3 React/TS
├── mobile/                         ← M4 React Native (Phase 1 이후)
└── packages/
    └── shared/                     ← 공유 타입(API 계약)·동기화 로직
```

> 처음에는 `ai-service/`, `backend/`, `web/`, `packages/shared/`만 생긴다. `mobile/`은 Phase 1에서 생성.

---

## 12. 하네스 운영 규칙 (Claude Code 세션 진행 방식)

> 이 섹션은 **매 세션을 어떻게 시작·진행·종료하는가**를 정의한다. §8(무엇을)과 짝을 이룬다.

### 12.1 세션 시작 (항상 이 순서)
1. `claude-progress.txt`를 읽어 직전 세션의 마지막 상태를 파악한다.
2. `feature_list.json`을 읽는다. **현재 활성 Phase에서 `"passes": false`인 항목 중 위에서부터 하나**를 고른다.
   - 빌드 순서(M1→M2→M3→M4)와 Phase 순서(0→1→2→3)를 건너뛰지 않는다.
   - 한 세션에 **한 항목**만. 여러 기능을 동시에 펼치지 않는다.
3. 그 항목에 해당하는 명세서 섹션(§4/§5/§8)을 확인해 범위를 확정한다.
4. `git log --oneline -10`으로 최근 작업을 확인한다.

### 12.2 작업 중
- 작은 단위로 구현 → 즉시 검증. 거대한 일괄 변경 금지(§8.3).
- 데이터 모델/API 계약 변경이 필요하면 **명세서를 먼저 고치고** 코드를 맞춘 뒤 응답에 명시한다(§3 규칙).
- 시크릿·근거(sources)·소유권 검증·캐싱 4가지를 구현 즉시 자가 점검한다(§3, §8.5).

### 12.3 항목 완료 기준 (passes:true 조건)
한 `feature_list.json` 항목은 **다음을 모두 만족할 때만** `"passes": true`로 바꾼다:
- 명세서가 정의한 입출력 계약대로 동작한다(임의 변형 없음).
- 해피패스 + 실패케이스(권한/쿼터) 테스트가 있고 통과한다(§7).
- 빌드·린트·타입체크 통과(깨진 채 커밋 금지, §7·§10).
- AI 관련 항목이면 응답에 `sources`가 실제로 포함된다(§3).
- **스스로 "괜찮아 보인다"로 끝내지 않는다. 실제 실행/테스트로 검증한 근거가 있어야 한다.**

### 12.4 세션 종료 (항상)
1. 완료 항목을 `feature_list.json`에서 `"passes": true`로 변경.
2. `git commit` (메시지 규약 §9: `feat(ai): ...`).
3. `claude-progress.txt`에 한 줄 추가: `[날짜] <항목id> 완료 / 다음: <다음 항목id>`.
4. 모듈 완료 시 해당 `README`와 명세서 §8 체크박스 갱신.

### 12.5 강제 장치 (이미 걸린 hook — 우회 시도 금지)
- **PreToolUse(guard)**: 시크릿 하드코딩·`.env` 커밋을 차단한다. 차단되면 우회하지 말고 환경변수로 옮긴다.
- **PostToolUse(verify)**: 파일 편집 시 lint/타입체크를 자동 실행한다. 실패가 나오면 **다음 작업으로 넘어가기 전에** 고친다.
- 이 hook들은 §3·§7·§10의 규칙을 기계적으로 강제하는 장치다. 통과를 우회·무력화하는 코드를 작성하지 않는다.

---
*이 파일이 명세서와 충돌하면 명세서(§3/§4/§5)가 이긴다. 규칙 변경이 필요하면 이 파일을 고친 뒤 그 사실을 응답에 남긴다.*