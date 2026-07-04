# ReadMind 진행 로그
> 세션 종료 시 CLAUDE.md §12.4에 따라 한 줄씩 추가.
> (2026-06-22부터 .txt → .md 로 전환. .txt 는 사내 Fasoo DRM 이 걸려 평문이 깨지므로 .md 를 정본으로 사용.)

[2026-06-15] 하네스 셋업 완료 (CLAUDE.md §12, feature_list 24항목, guard/verify hook).
다음: Phase 0 첫 항목 infra-compose (docker-compose + .env.example).

[2026-06-15] infra-compose 완료. docker-compose.yml(pgvector:pg16 + redis:7 + minio + createbuckets, ai-service/backend는 app 프로파일 분리), .env.example(§10 키 전체), infra/postgres/init/01-extensions.sql(CREATE EXTENSION vector), README, .gitignore 작성.
검증(라이브, docker.exe via WSL interop): compose config 통과 → up -d → postgres/redis/minio 모두 healthy → pgvector 0.8.2 등록 및 vector(3) 삽입/조회 성공 → MinIO readmind 버킷 생성 → redis PONG → down -v 정리. (호스트 5432 점유로 POSTGRES_PORT는 .env에서 변경 가능, compose는 ${POSTGRES_PORT:-5432}로 빼둠.)
주의: git 커밋 보류(사용자 결정) — 레포는 나중에 사용자가 직접 생성. WSL 기본 git은 /mnt/c(DrvFs) lock 파일 chmod 불가로 실패하므로, 커밋 시 Windows git.exe("/mnt/c/Program Files/Git/cmd/git.exe") 사용 권장. 현재 커밋 안 된 변경 파일: docker-compose.yml, .env.example, .gitignore, README.md, infra/postgres/init/01-extensions.sql, feature_list.json(infra-compose passes:true), docs 명세서 §8 체크박스, claude-progress.txt.

[2026-06-16] git 레포 연결: origin=github.com/Imvely/ReadMind, main 추적. 보류했던 infra-compose + 하네스 푸시 완료. 앞으로 모든 커밋은 이 레포로(Windows git.exe 사용, WSL git은 /mnt/c lock 문제로 금지).

[2026-06-16] ai-providers 완료. ai-service 스캐폴드 생성:
- app/core/config.py: pydantic-settings로 §10 env 로딩(LLM_PROVIDER/LLM_API_BASE/LLM_MODEL/LLM_API_KEY/EMBEDDING_MODEL/EMBEDDING_DIM=1024). embedding api_base/key 미설정 시 llm 값 재사용.
- app/providers/: LLMProvider/EmbeddingProvider Protocol(§5.7) + OpenAICompatLLM/OpenAICompatEmbedding 구현체 1개. OpenAI 호환 HTTP(httpx)로 상용/자체GPU(vLLM) 통합 — 벤더 직접결합 없음(§10). _http.py에 재시도(429/5xx)·타임아웃 공통화. errors.py ProviderError/ProviderConfigError.
- 임베딩 차원검증: 반환 벡터 len != EMBEDDING_DIM 이면 ProviderError(스키마 vector(1024) 불일치 방지).
- 검증(라이브, .venv Python 3.14): ruff check 통과 + pytest 17개 통과(해피패스/설정누락/HTTP4xx/503재시도/응답형식오류/차원·개수불일치/팩토리선택). httpx.MockTransport로 실제 LLM 없이.

[2026-06-16] ai-parse-pdf 완료. /ai/parse 파이프라인(§5.2):
- schemas/parse.py: ParseRequest{documentId,storageKey,format}→ParseResponse{chunkCount,language,pageCount}(camelCase alias).
- parsers/: 디스패처(format→parser) + pdf.py(PyMuPDF, 페이지별 텍스트, 텍스트 없으면 ParserError). base.py Page/ParsedDoc/ParserError/UnsupportedFormatError.
- chunking/: tokens.py(벤더無 토큰 추정 휴리스틱) + splitter.py(문단→문장→단어 경계, 500~800토큰 그리디팩, 오버랩80, page_no=기여토큰 최다 페이지).
- parse/: ports.py(Storage/ChunkRepository Protocol + ChunkRecord) + pipeline.py(run_parse, 포트 주입으로 인프라 없이 테스트). 임베딩은 EmbeddingProvider 경유(차원검증 재사용).
- storage/s3.py: S3Storage(boto3, MinIO/S3 공용). repositories/chunks_pg.py: PgChunkRepository(psycopg3+pgvector, delete후 insert=재파싱 멱등).
- api/: FastAPI app(main.py /health) + /ai/parse 라우터. deps.py Annotated DI. AI_SERVICE_TOKEN 설정 시 X-Service-Token 강제(§5 내부전용).
- config.py 확장: ai_service_token/postgres_url/s3_* 추가.
- 결정: documents.language/page_count/parse_status 갱신은 백엔드(be-doc-upload) 소유. AI는 값 반환+청크 저장만. document_chunks DDL은 M2 Flyway 소유(AI는 적재만).
- 검증(라이브, .venv py3.14): ruff 통과 + pytest 39개 통과. 그중 DB 통합 3개는 실제 Postgres(test_parse 격리스키마, pgvector vector(1024) roundtrip/멱등)로 검증, public 스키마 오염 0 확인. PDF는 실제 PyMuPDF 생성본 파싱.
- 주의: PyMuPDF base14 폰트는 CJK 미렌더 → 테스트 PDF 픽스처는 ASCII. requirements.txt에 fastapi/uvicorn/PyMuPDF/langdetect/boto3/psycopg[binary]/pgvector 추가됨.

[2026-06-16] ai-summarize-paper 완료. /ai/summarize(§5.3):
- schemas/summarize.py: SummarizeRequest{documentId,style(StrEnum PAPER|PLAIN, 기본 PAPER)} + PaperSummary{tldr,structure{objective,method,results,limitations,contribution},keypoints[],glossary[{term,desc}]} + PlainSummary{tldr,keypoints[]}. LLM 출력을 이 모델로 검증해 누락 차단.
- summarize/: prompts.py(스타일별 system + map/reduce 프롬프트, "본문에만 근거"), jsonout.py(코드펜스/잡텍스트 내성 JSON 파서), service.py(summarize: chunks 읽어 짧으면 단일호출, 길면 map-reduce. single_pass_tokens=6000/map_group_tokens=3000, 테스트는 kwargs로 축소), errors.py(SummarizeError).
- ports.py에 ChunkReader Protocol + ChunkText 추가. repositories/chunks_pg.py에 fetch_document_chunks(chunk_index 정렬) 추가.
- api/summarize.py 라우터 + main.py 등록. deps에 get_chunk_reader/get_llm 추가. SummarizeError→422, ProviderError→502.
- 결정: 본문은 document_chunks에서 읽음(재파싱 0회, 캐싱 철학). 소유권/쿼터는 백엔드 게이트 담당(AI 내부전용). 캐싱은 Spring summaries 몫.
- 검증(라이브, .venv py3.14): ruff 통과 + pytest 51개 통과. 요약 신규: 단일패스/map-reduce(json 1+note≥2 호출 검증)/PLAIN/펜스JSON/청크없음/스키마불일치/비JSON/라우터(200·기본PAPER·422·502·잘못된style). 모두 FakeLLM·FakeReader로 실 LLM/DB 없이.
- 주의(무해): starlette TestClient httpx deprecation + HTTP_422_UNPROCESSABLE_ENTITY 리네임 경고 2건. 동작 영향 없음.

[2026-06-16] ai-suggest-highlights 완료. /ai/suggest-highlights(§5.5):
- schemas/highlights.py: SuggestHighlightsRequest{documentId,limit(1~50,기본10)} + Highlight{pageNo,text,reason}.
- highlights/: prompts.py(원문 그대로 복사 강제) + service.py(suggest_highlights) + errors.py(HighlightError).
- 환각 방지 핵심: LLM이 고른 문장을 실제 청크 원문과 정규화 부분일치로 대조. 없으면 버림(지어낸 문장 제거). pageNo는 LLM 주장값이 아니라 매칭된 청크 page_no에서 가져옴. 중복제거 + limit 상한 + 최소길이(정규화 8자) 가드.
- 리팩터: jsonout을 app/summarize/ → app/core/jsonout.py로 이동(JsonOutputError). summarize/highlights가 각자 도메인 예외로 매핑(레이어 누수 방지). 기존 summarize 테스트 그대로 통과.
- api/highlights.py 라우터 + main 등록. HighlightError→422, ProviderError→502.
- 검증(라이브, .venv py3.14): ruff 통과 + pytest 63개 통과. 하이라이트 신규 12개: 해피(pageNo 원문유래)/환각드롭/limit/중복/짧은문장스킵/청크없음(LLM미호출)/비배열/비JSON/라우터(200 aliased pageNo·422·502·limit검증).
- M1 AI 4개 엔드포인트 중 3개 완료(parse/summarize/suggest-highlights). 남은 1개: qa.

[2026-06-16] ai-qa-rag 완료. /ai/qa(§5.4) RAG Q&A:
- schemas/qa.py: QaRequest{documentId,question,history?[{role,content}]} + QaResponse{answer,sources[{chunkIndex,pageNo,snippet}]}.
- ports.py: RetrievedChunk + ChunkRetriever Protocol. chunks_pg.py: search_similar_chunks(embedding<=>%s::vector 코사인, document_id 스코프, ORDER BY distance LIMIT k). 주의: 리스트 파라미터는 기본적으로 double precision[]로 바인딩됨 → vector 리터럴 "[...]"::vector 캐스팅 필수(register_vector만으론 연산자 매칭 실패).
- rag/: prompts.py(발췌 근거+인용 강제) + qa.py(answer_question) + errors.py(QaError).
- 환각 방지(§3) 핵심: 질문 임베딩→top-k 검색→LLM {answer,citations[chunkIndex]}. citations를 검색결과와 교차검증, 없는 인용 버림. snippet/pageNo는 실제 청크에서만. **검증된 근거 0개면 답을 NO_EVIDENCE("문서에서 답을 찾을 수 없습니다")로 강등** — 근거 없는 답변 절대 안 냄. 검색결과 0이면 LLM 호출도 안 함.
- api/qa.py 라우터 + main 등록. deps get_chunk_retriever 추가. QaError→422, ProviderError→502.
- 검증(라이브, .venv py3.14): ruff 통과 + pytest 79개 통과. qa 신규 16개: 해피(answer+sources, 질문 임베딩 확인)/인용교차검증/근거없음강등/검색0(LLM미호출)/중복인용/문자열인용coerce/비JSON/빈답변 + 라우터(200 aliased·근거없음·422·502·history) + 실 DB 벡터검색 3개(코사인 거리정렬·k제한·문서스코프, test_rag 격리스키마). public 오염 0 확인.

[M1 Phase0 AI 4종 완료] parse/summarize/suggest-highlights/qa. 명세서 §8 P0 "AI 서비스" 체크박스 [x]. provider 추상화도 [x].

[2026-06-19] be-auth-jwt 완료. M2 백엔드 진입(§4.1).
- 발견: 이전 세션이 backend/ auth 코드를 커밋·기록 없이 남겨둠(미완). 점검 결과 코드 품질 양호 → 갈아엎지 않고 검증+테스트 보강으로 완료.
- 구조: AuthController(/auth signup·login·refresh·me) → AuthService → UserRepository. 공통래퍼 ApiResponse{success,data/error} + GlobalExceptionHandler(ErrorCode→HTTP). JwtTokenProvider(HS256, access/refresh를 type 클레임으로 분리, 32바이트 강제). SecurityConfig(STATELESS, signup/login/refresh permitAll, 나머지 authenticated) + JwtAuthFilter(principal=userId:Long) + JwtAuthenticationEntryPoint(401 공통래퍼). User 엔티티는 Flyway 스키마에 맞춰 ddl-auto:none. JwtProperties/QuotaProperties는 @ConfigurationProperties(env 주입, JWT_SECRET 미설정 시 기동 실패).
- V1__init.sql: §3 스키마 전체(users/subscriptions/usage_quotas/documents/document_chunks 등) 이미 작성돼 있음 — Flyway가 스키마 SSOT.
- 빌드 검증: gradle wrapper가 통째로 없어 빌드 불가였음 → 캐시된 gradle 8.11.1로 wrapper 생성(gradlew/gradle-wrapper.jar). build.gradle에 mockito-kotlin:5.4.0 testImpl 추가.
- 검증(라이브, ./gradlew test): 16개 통과(0 실패). JwtTokenProviderTest 7(발급·검증/type분리/위조/만료/손상, DB無 순수단위) + AuthControllerTest 9(@WebMvcTest 슬라이스+AuthService mock+실제 SecurityConfig 임포트: signup 200/검증400/중복409, login 200/자격401, refresh 200, me 토큰없음401·손상토큰401·해피200). DB/Flyway 없이 컨트롤러·보안필터 계층 검증.
- 결정: §8 P0 "백엔드" 체크박스는 회원/로그인+업로드+위임캐싱을 한 줄로 묶어 be-doc-upload/be-ai-gate-cache 완료 시 함께 [x](이번엔 보류). 쿼터 차감은 be-quota-tiers 소관 — me는 티어별 한도만 노출(used=0).

[2026-06-22] be-doc-upload 완료 (§4.2). 문서 업로드 파이프라인 — 직전 세션이 구현했으나 커밋·기록 누락 → 검증 후 마무리 커밋.
- 구조: DocumentController(/documents) → DocumentService → DocumentRepository(JPA). create(presigned PUT URL 발급 + PENDING row) → complete(PARSING 전환 + AI /ai/parse 비동기 트리거) → list/get/content(presigned GET)/delete(tombstone). 응답 공통래퍼 ApiResponse.
- 소유권(§3): 모든 조회/수정이 ownedOrThrow(findByIdAndUserIdAndDeletedAtIsNull) 경유. 미소유/삭제됨은 NOT_FOUND. list도 user_id 스코프.
- 캐싱 철학(§3): complete 시 이미 READY면 재파싱 안 함. 포맷 가드 = Phase0 PDF만(SUPPORTED_FORMATS).
- AI 격리(§5): document/ai/AiParseClient(AI_SERVICE_TOKEN→X-Service-Token), DocumentParseRunner(@Async, parse_status PENDING→PARSING→READY/FAILED). config/: S3Config+S3Properties(presigner, MinIO/S3), AiServiceProperties, AsyncConfig.
- 검증(라이브, ./gradlew test --rerun-tasks): 단위 40개 통과(auth 16 + document 24: DocumentControllerTest 9·DocumentServiceTest 9·DocumentParseRunnerTest 3·DocumentStorageTest 3). ContextLoadSmokeTest는 @Tag("integration")으로 기본 제외(Postgres readmind_smoke 필요, ./gradlew integrationTest).
- 진행 로그 .txt → .md 전환(사내 Fasoo DRM이 .txt 평문을 깨뜨림). CLAUDE.md §12.1/§12.4 참조도 .md로 갱신.

[2026-06-22] be-ai-gate-cache 완료 (§4.4, §3). AI 위임 + 쿼터 게이트 + 캐싱:
- 신규 모듈 com.readmind.ai: AiController(/documents/{id}/summarize·/qa) → AiService → (DocumentService 소유권/상태, QuotaGate, AiContentClient, SummaryRepository, Qa*Repository).
- 강제 순서(CLAUDE.md §5): 소유권 검증 → 쿼터 게이트(검사) → 캐시 조회 → AI 호출 → 캐시 저장 → 쿼터 차감. **캐시 히트 시 AI 미호출·쿼터 미차감(변동비 0)** — AiServiceTest로 검증.
- 소유권/상태(§3): documents.get(userId,id)로 소유권 검증(미소유 NOT_FOUND) + parseStatus!=READY면 VALIDATION. QA 세션도 findByIdAndUserIdAndDocumentId로 소유권 검증.
- 쿼터(com.readmind.quota): QuotaGate(ensureWithin 검사 / record 차감) + UsageQuota 엔티티. FREE만 한도(QuotaProperties), PRO/STUDENT는 Phase0 무제한. 월 단위 집계 — period_start 달 바뀌면 리셋. TimeConfig의 Clock 빈으로 테스트 결정성. **전면 티어 매트릭스는 be-quota-tiers(Phase2)**.
- 근거(§3): /qa는 AI /ai/qa의 sources를 그대로 통과({page,snippet}로 매핑) + qa_messages.sources(jsonb) 저장. 근거 0개 강등은 AI 서비스가 담당.
- 캐싱: summaries UNIQUE(document_id,scope,scope_ref,style). Phase0은 scope=DOCUMENT만(SECTION/PAGE_RANGE 거부) → 캐시키 (문서,스타일)로 단순화. content는 AI JSON을 jsonb 그대로 보관(재모델링 안 함, 계약 보존). jsonb는 @JdbcTypeCode(SqlTypes.JSON) on String.
- AI 격리(§5): AiContentClient(RestClient, X-Service-Token) — summarize/qa. RestClientException→ApiException(INTERNAL)로 변환해 실패 시 캐시저장·차감 안 일어나게.
- 계약 변경(§3 절차 준수): 명세서 §4.4 /qa 티어를 PRO→FREE(소량)/PRO로 완화(Phase0 베타 경로가 FREE 질문 필요). 명세서 먼저 수정 후 코드 반영. 사용자 승인됨.
- 검증(라이브): ./gradlew test 단위 62개(auth16+document24+ai22: AiServiceTest11·QuotaGateTest6·AiControllerTest5) + ./gradlew integrationTest 5개(ContextLoadSmokeTest2 전체 컨텍스트 부팅 + AiPersistenceIntegrationTest3 실 Postgres jsonb 라운드트립, @Transactional 롤백으로 오염0). 모두 0 실패. readmind_smoke DB(localhost:5432) 사용.
- 명세서 §8 P0 "백엔드" 체크박스 [x](auth+upload+위임캐싱 3종 완료).

[M2 Phase0 백엔드 완료] be-auth-jwt + be-doc-upload + be-ai-gate-cache. 다음은 M3 웹.

[2026-07-01] web-upload-viewer 완료 (§6.1, M3 진입). 웹 MVP — 직전 세션이 스캐폴드를 커밋·기록 없이 남겨둠(be-auth/be-doc-upload와 동일 패턴) → 갈아엎지 않고 검증+테스트 보강으로 마무리.
- 모노레포: 루트 package.json(workspaces: packages/*, web). packages/shared = API 계약 타입 SSOT(§3) — ApiResponse<T> 래퍼, Auth/Document/Summarize/Qa DTO 전부 백엔드 Kotlin DTO(camelCase)와 1:1. 웹은 중복 정의 안 함.
- web/ 구조: lib/api.ts(공통 래퍼 언랩 + ApiClientError code 보존 + 401→refresh 1회 재시도 + presigned PUT) + lib/tokens.ts(localStorage). api/{auth,documents,ai}.ts, hooks/{documents,ai}.ts(TanStack Query — 서버상태, useEffect 패칭 없음), store/auth.ts(Zustand, 부트스트랩만 useEffect). pages/{Login,Library,Reader}. features/reader/{PdfViewer(pdf.js 어댑터 격리),SummaryPanel,QaPanel,pdf.ts}.
- 핵심 요구 충족: Q&A sources[{page,snippet}] → 칩 클릭 → ReaderPage의 pdfRef.scrollToPage(page) → 해당 페이지 scrollIntoView + 1.6s 강조. PdfViewer는 forwardRef+useImperativeHandle(scrollToPage). 렌더러 어댑터 격리로 epub 교체 가능(§5).
- 쿼터(§3): QUOTA_EXCEEDED code를 QaPanel/SummaryPanel이 업셀 안내로 분기. page null 근거는 점프 비활성.
- 막힘 해결: vite.config.ts의 node:url 타입 누락 → @types/node 설치 + tsconfig types에 "node" 추가. 타입체크 통과.
- 검증(라이브): npm run typecheck(shared+web strict 통과) + npm run build(tsc+vite build 성공, dist 산출) + npm run test(vitest 15개 통과). 테스트 신규 3파일: api.test.ts 7(성공언랩/Authz헤더/에러code매핑/QUOTA_EXCEEDED/401→refresh재시도·새토큰검증/refresh없음시무재시도/noAuthRetry) + QaPanel.test.tsx 4(답변+근거렌더·근거클릭→onJumpToPage/page null 비활성/쿼터업셀/빈질문차단) + SummaryPanel.test.tsx 4(PAPER구조렌더/PAPER mutate/캐시배지/쿼터업셀). fetch/hook 모킹으로 실 백엔드 없이.
- 미검증(다음): 브라우저 라이브 e2e(실 백엔드+AI+실 PDF 업로드→파싱→요약→QA점프)는 스택 기동 필요해 이번 세션 범위 밖. 단위/컴포넌트 레벨은 전부 그린.

[2026-07-01] 배포 컨테이너화 완료 (p0-beta-validate 선행). commit f1ad9ae.
- compose가 build: ./ai-service, ./backend(app 프로파일)를 참조하는데 정작 Dockerfile이 없어 배포 불가였음 → 두 Dockerfile 신규.
- ai-service/Dockerfile: python:3.12-slim + requirements 캐시 레이어 + 비루트(uid1001) + `uvicorn app.main:app :8000`. .dockerignore로 .venv/tests/캐시 제외.
- backend/Dockerfile: 멀티스테이지(gradle:8.11.1-jdk21 `bootJar -x test` → eclipse-temurin:21-jre, 비루트) :8080. .dockerignore로 build/.gradle 제외. clean bootJar은 boot jar 1개만 생성(-plain.jar은 풀빌드 잔재) → COPY glob 안전 확인.
- 계약/설정: application.yml은 SPRING_DATASOURCE_URL(JDBC)을 읽는데 .env엔 POSTGRES_URL(psycopg형)만 있어 키/형식 불일치 → compose backend에 environment로 `SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}` 명시 매핑. username/password는 POSTGRES_USER/PASSWORD 재사용. ai-service는 POSTGRES_URL/S3_ENDPOINT(둘 다 컨테이너 서비스명)를 직접 읽어 그대로 정합.
- 검증(라이브, Docker Desktop): `docker compose --profile app build` 성공(ai 506MB / be 589MB). up 후 5개 컨테이너 healthy(postgres/redis/minio/ai/backend). backend가 실 readmind DB에 Flyway V1 마이그레이션 성공 + Tomcat :8080 기동. ai /health={status:ok}. signup→login→me(quota FREE 한도 노출)→중복 signup=EMAIL_EXISTS 왕복 정상. (콜드스타트 직후 첫 요청 1회 401은 이후 재현 안 됨, 무해.)
- 환경 이슈 기록: (1) 리포의 .env는 예전 통합테스트용 최소본(POSTGRES 3개만, JWT_SECRET 등 공란)이라 스모크용으로 JWT_SECRET/MinIO/S3/AI 키를 로컬 추가함(원본 백업 scratchpad/env.backup, POSTGRES creds=readmind/readmind/readmind 보존). 실사용 시 JWT_SECRET·LLM 키 실값 필요. (2) 호스트 8000을 타 프로젝트 컨테이너 ocr-ingest-api가 점유 → ai-service 퍼블리시만 8001로 옮기는 override 필요(backend↔ai는 내부망 ai-service:8000이라 무관).
- AI 실경로(parse/summarize/qa)는 LLM_API_KEY(유료) 필요해 미검증 — p0-beta-validate에서 실 키로 e2e.

[2026-07-01] Gemini provider(LLM+임베딩) + 배포 산출물 + 사내 CA. (p0-beta-validate 선행 계속)
- 웹 컨테이너화: web/Dockerfile(루트 컨텍스트 멀티스테이지→nginx SPA) + deploy/Caddyfile(자동HTTPS, /api→backend·그외→web, same-origin이라 CORS無) + compose web/caddy 서비스. 검증(라이브): caddy 통해 /(SPA)·/api(실 JWT)·SPA폴백 정상. commit 835a74f.
- 배포 컨테이너화(선행): ai-service/backend Dockerfile + compose SPRING_DATASOURCE_URL 매핑. 실 스택 up→Flyway V1 마이그레이션·signup/login/me 왕복 확인. commit f1ad9ae. (web-upload-viewer 커밋의 api.test tsc에러도 f6d6b82로 수정 — 테스트 작성 후 vitest만 돌리고 build 재확인 누락했던 것.)
- Gemini LLMProvider(§5.7): providers/llm.py GeminiLLM(google-genai, 지연임포트) + _gemini.py 공용헬퍼(build_client·run_with_retry·resolve_vertex). json_mode→response_mime_type, Vertex Express 자동감지("AQ."키=vertexai). get_llm_provider에 gemini 분기 → 요약/QA 코드변경 없이 자동경유. commit 314ade9.
- Gemini EmbeddingProvider: GeminiEmbedding(gemini-embedding-001, output_dimensionality=1024→vector(1024) 일치, L2정규화, 배치100). get_embedding_provider가 embedding_provider(미설정시 llm_provider 추종)=gemini 선택 → QA검색/parse 자동경유. commit 3e0f049.
- 검증: ruff 통과 + pytest 102개(신규 Gemini LLM 12 + 임베딩 11, 페이크 client 주입 SDK/네트워크 무관). 프레시 컨테이너 실 google-genai 2.10.0으로 팩토리 오프라인 구성 확인.
- **환경 해저드 2건(메모리 저장됨)**: (1) Fasoo DRM이 venv의 entry_points.txt 14개 암호화 → 로컬 pytest는 `PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 PYDANTIC_DISABLE_PLUGINS=1` 로 우회(Docker는 프레시 venv라 무관). (2) 사내 프록시 HTTPS MITM(self-signed CA) → 컨테이너에서 외부 HTTPS(R2·Gemini) 인증서검증 실패.
- 사내 CA 대응: ai-service/Dockerfile이 certs/*.crt를 신뢰번들에 자동추가(certifi append + REQUESTS/AWS_CA_BUNDLE·SSL_CERT_FILE env). cert없으면 no-op(빌드 안깨짐, 검증됨). deploy/CORP_CA_GUIDE.md에 따라하기 런북(CA추출 PowerShell→override→재빌드→CA검증→브라우저 e2e→R2 CORS). commit 49a9deb.
- 사용자 결정: LLM=Gemini 무료티어(gemini-2.5-flash, 키는 Vertex Express "AQ."형). 스토리지=Cloudflare R2(readmind-dev 버킷). B(CA주입)+라이브 e2e는 사용자가 직접 실행(가이드 제공, 나는 서버 호출 안 함).

[2026-07-01] 라이브 e2e 통과 (Gemini 무료티어 + Cloudflare R2 + 사내 CA). commit beb5f06.
- 사내 CA: Windows 신뢰저장소에서 프록시 CA 추출(CN=lotte.net, O=LDCC) → ai-service/certs/corp-root.crt → 재빌드 후 컨테이너에서 google/Gemini/R2 TLS 전부 통과 확인.
- 실측으로 통합 버그 2개 발견·수정: (1) AI Studio 키가 "AQ."로 시작할 수 있어 접두사로 Vertex/Developer 구분 불가 → resolve_vertex 기본을 Developer API로(자동감지 제거). AQ.키를 Vertex로 잘못 보내 403(project API 미활성) 나던 것 해결. (2) backend→ai 호출 400 "Unsupported upgrade request": JDK HttpClient 기본 HTTP/2 h2c 업그레이드를 uvicorn이 거부 → config/AiHttp.kt로 AI 호출을 HTTP/1.1 고정(AiParseClient/AiContentClient).
- **전체 e2e 성공**: 업로드→R2 PUT 200→parse READY(6p, Gemini 임베딩 1024d)→summarize 200(Gemini PAPER 한국어 요약)→qa 200(근거 sources 포함; 무관 질문은 NO_EVIDENCE 강등=환각방지 정상). ai-service ruff+pytest 103, backend ./gradlew test 통과.
- 운영 메모: 로컬 e2e는 docker exec(컨테이너가 CA신뢰+내부망 backend:8080)로 실행. Windows Git Bash에서 docker exec/cp 시 MSYS_NO_PATHCONV=1 필요(/tmp 경로 변환 방지). docker-compose.override.yml(gitignored)로 ai-service 8000 퍼블리시 제거(ocr-ingest-api 충돌).
- 남은 것: 브라우저 실사용엔 R2 CORS 정책(CORP_CA_GUIDE.md STEP6) 필요(서버사이드 e2e엔 불필요). Phase0 AI 경로 전부 실동작 확인됨.

[2026-07-01] be-annotations-crud 완료 (M2 Phase1 진입, active_phase 0→1). commit 1f84e29.
- com.readmind.annotation 모듈(§4.3): Highlight/Note/Bookmark/ReadingProgress 엔티티 + 리포 4 + Service + Controller + DTO. Flyway 스키마에 이미 4테이블 완비라 마이그레이션 불필요(ddl-auto:none 매핑만).
- 매핑: location=JSONB(@JdbcTypeCode JSON, raw 통과 포맷무관), tags=text[](@JdbcTypeCode ARRAY, Array<String>), 진행률=복합키(user_id,document_id) @IdClass upsert. 삭제=소프트(deleted_at 톰스톤)+version 증가(동기화 §4.5 대비).
- 소유권(§3): documents/{id} 하위는 문서 소유권 선검증, /highlights|notes|bookmarks/{id}는 user_id 스코프 조회→미소유 NOT_FOUND.
- 계약 갱신(§3 절차): 명세서 §4.3에 notes PATCH/DELETE·bookmarks DELETE·progress GET 추가 후 구현. search는 be-highlight-search 소관.
- 검증: ./gradlew test 통과(AnnotationServiceTest 해피+소유권/유효성 실패). 라이브(실 Postgres, 컨테이너 exec): tags[]·jsonb 왕복, patch/version, 진행률 복합키 upsert 덮어쓰기, 소프트삭제 제외, location누락 400, 미소유 404 전부 OK.
- 함정 메모: Kotlin KDoc 안에 "/*"(예: /documents/{id}/*)는 중첩주석으로 파싱돼 컴파일 깨짐 — 경로 예시는 "/*" 안 쓰게 서술.

[2026-07-01] be-highlight-search 완료 (M2 Phase1, 유료 핵심). commit ce161a9.
- HighlightRepository.search 네이티브 쿼리: selected_text/note ILIKE(대소문자무시) + tag ANY(tags) 배열매칭 + documents 조인(소유권·soft-delete). q/tag 는 CAST(:x AS text) IS NULL 가드로 선택적(둘 다 없으면 전체·사용자스코프). 페이지네이션 + 결과에 documentTitle.
- DocumentRepository.findByIdInAndUserId(소유권 스코프 제목 일괄) 추가. Service.searchHighlights(blank→null, 제목매핑). Controller GET /highlights/search(q,tag,page,size) — GET이라 /highlights/{hid} PATCH/DELETE와 충돌 없음.
- 검증: ./gradlew test(검색 케이스). 라이브(실 Postgres): q 대소문자무시, tag ANY, q+tag AND, 미매칭 0, 다른 사용자 0(격리), documentTitle 채워짐 전부 OK.

[2026-07-01] DB를 Neon(클라우드 Postgres)으로 전환 + V1 마이그레이션 적용. commit f5cd230.
- 사용자 결정: DB=Neon. .env POSTGRES_URL 에 Neon 접속문자열(풀러, sslmode=require), pgvector는 Neon SQL Editor에서 CREATE EXTENSION 완료.
- V1__init.sql 적용: flyway/flyway 도커 CLI 로 Neon '다이렉트' 엔드포인트(-pooler 제거)에 migrate. 다이렉트를 쓴 이유=Flyway 세션 어드바이저리 락이 PgBouncer 트랜잭션 풀러와 안 맞음. 결과 now at v1, pgvector 기존이라 skip.
- 검증: Neon에 14개 테이블 + document_chunks.embedding=vector(1024) + pgvector 확인. sslmode=require는 암호화만/인증서 미검증이라 사내 MITM CA 없이 접속됨(그래서 사용자가 강조).
- 앱 반영: (1) ai-service는 POSTGRES_URL(풀러,sslmode=require) 그대로 사용 — Neon 조회 성공. (2) backend는 compose SPRING_DATASOURCE_URL을 ${..:-로컬기본}으로 오버라이드 가능화 + .env에 SPRING_DATASOURCE_URL(Neon 다이렉트 JDBC,sslmode=require)/USERNAME/PASSWORD 추가(env가 application.yml POSTGRES_USER 기본을 덮음). backend 재기동→Neon 부팅+Flyway validate 통과+signup이 Neon users에 기록(id=1) 확인.
- 운영: 마이그레이션용 flyway.conf(비밀 포함)는 스크래치패드에 만들고 사용 후 삭제, 커밋 안 함. backend 스키마 변경 시 앱 기동 Flyway가 다이렉트로 적용(락 OK). 로컬 postgres 컨테이너는 이제 미사용(compose엔 남아있음).

[2026-07-01] AI서비스 HF Space 배포 + ai-parse-multi 완료.
- HF Space 배포: dayeongim/readmind-ai(Docker SDK). Space repo 클론→app/·requirements 복사 + HF규칙 Dockerfile(7860/user uid1000/--chown=user/USER user) + README frontmatter(sdk:docker, app_port:7860). .env의 HF_TOKEN으로 git push(인증 전용, 파일/커밋/원격config에 토큰 미저장). 커밋 "deploy: 실제 AI서비스 배포". 로컬에서 동일 이미지 빌드·실행→/health·/docs·/openapi 200 검증. Space Public 전환(사용자). 점검: AI코드에 HF 라이브러리 전무(임베딩·요약 다 Gemini)→HF_TOKEN 런타임 불필요. /ai/*는 AI_SERVICE_TOKEN(Depends(verify_service_token))로 보호→공개돼도 Gemini 남용 불가. **TODO(메모리): HF Space /docs·/openapi 비공개 처리(추후).** 사내망에선 *.hf.space 프록시 차단이라 여기서 호출검증 불가(배포환경 밖에서).
- ai-parse-multi 완료(commit da23dd4): parsers/txt.py(인코딩 순차)·docx.py(python-docx 문단+표)·epub.py(ebooklib 스파인별, nav제외, BeautifulSoup, 임시파일 경유) + _REGISTRY 등록. requirements에 ebooklib/beautifulsoup4/python-docx. backend SUPPORTED_FORMATS를 PDF+EPUB+TXT+DOCX로 확장. 검증: ruff+pytest 114(신규 11, 실제 docx/epub 픽스처 생성) + 재빌드한 컨테이너에서 파서 동작 스모크(supported: docx/epub/pdf/txt). 기존 미지원포맷 테스트는 hwp로 갱신.

[2026-07-01] ai-translate 완료 (M1+M2, 원문대조 번역). commit 2fb615c.
- AI(ai-service): schemas/translate.py + translate/{prompts,service,errors}.py + api/translate.py → main 등록. LLM provider(Gemini) 경유 충실 번역(의미 가감 없음, 번역문만), 언어코드→이름 매핑, 20k자 가드, json_mode 미사용. 응답 {translated, sourceExcerpt(원문), targetLang}(원문대조). TranslateError→422, ProviderError→502.
- backend: AiContentClient.translate + AiService.translate(소유권→쿼터게이트→AI→차감, 캐시 없음-임의텍스트, READY 불필요) + AiController POST /documents/{id}/translate + Translate DTO. 쿼터는 기존 QuotaKind.TRANSLATE 재사용(이미 완비돼 있었음).
- 계약(§3): 명세서 §4.4 translate를 Phase1 선택텍스트 기반 {text,targetLang}→{translated,sourceExcerpt,targetLang}로 명시.
- 검증: ai-service ruff+pytest 123(신규 test_translate 9), backend ./gradlew test(AiServiceTest translate 3: 해피·쿼터초과 미호출미차감·미소유404). **라이브 e2e(실 Gemini, Neon)**: 자체 유저+문서 생성→번역 200("Transformers process sequences in parallel using self-attention.")·원문대조·빈텍스트422·미소유404 확인.
- 미완(별도): desc의 "자동 하이라이트 추천 UI 연동"은 web(M3) 작업 — suggest-highlights AI 엔드포인트는 이미 존재(ai-suggest-highlights), 리더 UI에서 소비만 남음. 번역 API 계약(§5/§4.4)은 완료.
- 운영 메모: backend가 Neon 사용 이후 로컬 postgres의 예전 유저/문서(e2e@)는 Neon에 없음 → e2e 스크립트는 자체 signup+문서생성으로.

[2026-07-02] Cloud Run 백엔드 배포 완료 (p0-beta-validate 인프라). 코드 아님 — 배포/운영.
- `gcloud run deploy readmind-backend --source=./backend`(Cloud Build가 backend/Dockerfile로 빌드→Artifact Registry→배포) → 프로젝트 gen-lang-client-0471683922, 리전 asia-northeast3. 서비스 URL: https://readmind-backend-53543020852.asia-northeast3.run.app (--allow-unauthenticated, 1Gi/1cpu/--cpu-boost, min0/max3, timeout300).
- 시크릿 7종 Secret Manager 경유(--set-secrets, 값은 콘솔에서 사용자가 직접 입력): readmind-datasource-{url,username,password}, readmind-jwt-secret, readmind-r2-{access-key,secret-key}, readmind-ai-service-token. 비민감(AI_SERVICE_URL=HF Space, S3_ENDPOINT/BUCKET/REGION/PATH_STYLE)은 --set-env-vars. PORT는 Cloud Run 주입이라 설정 금지(넣으면 거부).
- 런타임 SA(53543020852-compute@developer.gserviceaccount.com)에 각 시크릿 secretAccessor 부여.
- 검증: Creating Revision→Routing traffic done = 컨테이너 기동+Flyway Neon 마이그레이션+Tomcat PORT 바인딩 성공. HTTP e2e(GET /api/v1/auth/me→401, POST /api/v1/auth/signup→200)는 사내 MITM으로 내 환경에선 *.run.app 검증 불가 → 사용자 Cloud Shell에서 확인.
- 함정: (1) 시크릿을 먼저 만들지 않고 deploy하면 빌드는 되고 Creating Revision에서 "Secret ... not found"로 실패 → 생성→grant→deploy 순서 필수. (2) --source는 Cloud Shell CWD 기준 → 반드시 ~/ReadMind 안에서 실행(홈에서 하면 could not find source [./backend]). (3) 모든 gcloud 호출에 "Regional Access Boundary ... Account not found: 24eebddd5e|llmdayeong@gmail.com"(활성계정 lim.dayeong과 다른 유령 principal) 반복 — 재시도 후 진행돼 비치명적이나 계정/조직 정책 이상, 추후 정리.
- 런북: deploy/cloudrun-backend.md (시크릿 이름/grant/deploy 명령 — 값 미포함, 커밋 안전).
- 남은 것: AI 실경로(Cloud Run→HF Space) e2e, 웹 프론트 API base를 이 URL로 + R2 CORS.

[2026-07-02] 클라우드 전 구간 e2e 성공 (p0-beta-validate 가치경로 실동작). commit 0ed2960까지.
- 흐름 통과: Cloud Run 백엔드 → HF Space(ai-service) → Gemini → R2 → Neon. 업로드→R2 PUT 200→parse READY(txt)→summarize PAPER(한국어)→qa(answer+sources[{page,snippet}]=근거 포함, §3 라이브 충족).
- 발견·해결(순서대로): (1) e2e R2 presigned PUT을 stdin 파이프로 하면 chunked→411 → 임시파일 업로드로 Content-Length 설정(9444e01). (2) HF Space가 ai-parse-multi 이전 이미지라 txt=415 UnsupportedFormat → deploy/hf-space-deploy.sh로 app/+requirements만 재동기화 push(b30fec0). (3) Gemini AQ.(Vertex Express) 키 무료 크레딧 소진→429 RESOURCE_EXHAUSTED → AI Studio Developer API 무료키(AIza)로 LLM_API_KEY 교체(HF Space Settings). 코드 기본이 Developer API 모드라 그대로 동작.
- 웹: API_BASE=VITE_API_BASE_URL, vite dev 프록시=VITE_DEV_API_TARGET로 전환 가능(b53e938). 로컬 dev가 Cloud Run 보게 하려면 web/.env.local에 VITE_DEV_API_TARGET=<Cloud Run URL>(프록시라 백엔드 CORS 불필요). 백엔드엔 CORS 미설정 — cross-origin 직접호출 시 추가 필요.
- 진단 도구: deploy/diag-ai-parse.sh(백엔드 업로드 storageKey로 Space /ai/parse 직접 호출→502 detail 노출). 운영: Cloud Run @Async 파싱은 --no-cpu-throttling 필수(revision 00005 적용).
- 보안: 사용자가 채팅에 HF_TOKEN 평문 노출 → 폐기/재발급 요청함(사용자 처리 예정).
- 남은 것: R2 CORS(브라우저 업로드용, 서버사이드 e2e엔 불필요), p0-beta-validate의 재사용률 계측(코드 아님·실사용), (선택)백엔드 CORS.

[2026-07-02 저녁 — 세션 종료] 하루 종일 Cloud Run 배포 + 클라우드 e2e 디버깅. commit c3b0268 이후 이 커밋으로 정리.

■ 오늘 완료(검증됨):
- 백엔드 Cloud Run 배포 + 라이브(signup 200). 시크릿 7종 Secret Manager. 런북 deploy/cloudrun-backend.md.
- 클라우드 AI 전 구간 e2e 성공(CLI): parse→summarize(한국어)→qa(근거 sources 포함). 스크립트: deploy/e2e-cloudrun.sh(txt), deploy/e2e-cloudrun-file.sh(파일/PDF·백엔드경로), deploy/diag-ai-parse.sh·diag-ai-parse-pdf.sh(Space 직접호출 동기진단).
- **PDF parse 500 근본해결**: PDF 추출 텍스트의 NUL(0x00) → PostgreSQL text 컬럼 거부 → psycopg.DataError(ai-service/app/repositories/chunks_pg.py:68 executemany) → 500. 수정: 모든 파서 공통 출력 Page 생성 시 sanitize_text로 NUL·C0 제어문자 제거(ai-service/app/parsers/base.py). 테스트 ai-service/tests/test_base_sanitize.py. Space 재배포(deploy/hf-space-deploy.sh)로 반영. commit c3b0268.
- 웹 Cloud Run 연결: API_BASE=VITE_API_BASE_URL, dev프록시=VITE_DEV_API_TARGET(web/.env.local, gitignore). 서재 목록 폴링 버그 수정(web/src/hooks/documents.ts useDocumentsQuery에 refetchInterval 추가 — PENDING/PARSING 있으면 2초 폴링).
- 최종 검증: id=25(CLI 백엔드경로 PDF)·id=28(직접 Space) 둘 다 200 + chunkCount=3 + 요약/qa 정상. 샘플=LoRA 논문 앞3p.

■ 막힘(다음 세션 최우선):
- **브라우저 업로드만 parse 실패**: 브라우저 PDF 업로드 → "분석중"에서 곧바로 FAILED. 그런데 동일 파일/동일 백엔드경로가 CLI(deploy/e2e-cloudrun-file.sh → id=25)·직접Space(diag → id=28)에선 성공. 실패 예: id=27(08:56). 앞뒤 id=25/28은 성공 → 간헐적/브라우저 특이.
- 실패가 "바로"(즉시)라는 게 핵심: 정상 parse는 ~9초인데 즉시 FAILED = 백엔드 @Async가 Space 성공응답 전에 빠르게 예외. DocumentParseRunner.run의 catch(backend/src/main/kotlin/com/readmind/document/DocumentParseRunner.kt:35→38 log.error "파싱 실패")에서 FAILED. AI 호출은 AiParseClient(backend/src/main/kotlin/com/readmind/document/ai/AiParseClient.kt:42-51, 재시도 없음).
- 사유 미확인: gcloud logging read 가 이 환경에서 신뢰 불가(시간창 쿼리 empty, freshness도 최근분 empty; Regional Access Boundary 404 노이즈 매번). 브라우저 실패문서의 예외를 못 잡음.
- **다음 확인법(가장 빠른 갈림)**: 브라우저 업로드 직후 HF Space **Logs 탭**(https://huggingface.co/spaces/dayeongim/readmind-ai) 확인 →
    (a) /ai/parse 요청이 안 오면 = 백엔드가 Space 호출 전/네트워크에서 실패 → AiParseClient·config/AiHttp.kt·Cloud Run async/인스턴스 확인
    (b) 요청은 오는데 에러면 = Space traceback 확인.
- 가설: Space 재배포 콜드스타트 5xx를 AiParseClient가 재시도 없이 FAILED 처리(콜드스타트 창에 걸린 문서만 실패). 견고화안: AiParseClient에 전이오류(5xx/타임아웃) 재시도, 또는 documents에 parse_error 컬럼 추가해 사유를 API로 노출(디버깅 편의).

■ 다음 먼저 할 것:
1. 브라우저 재업로드 + HF Space Logs 동시확인 → (a)/(b) 판별 → 원인 확정.
2. 확정 후 견고화(AiParseClient 전이오류 재시도) → 브라우저 e2e 완주 → p0-beta-validate 진행.

■ 참고 컨텍스트:
- 샘플 PDF: 로컬 C:\Users\LOTTE\Downloads\LoRA_3p.pdf, Cloud Shell ~/LoRA_3p.pdf (LoRA 논문 앞3p, PyMuPDF로 자름). Q&A 검증질문 "LoRA는 전체 파인튜닝 대비 학습 파라미터/GPU 메모리를 얼마나 줄이나요?"(정답 10,000x/3x, 근거 page1).
- Cloud Run: URL https://readmind-backend-53543020852.asia-northeast3.run.app (context-path /api/v1). 프로젝트 gen-lang-client-0471683922 / 번호 53543020852 / asia-northeast3. @Async parse엔 --no-cpu-throttling 필요(적용됨).
- Gemini: 무료 AI Studio Developer 키(AIza) 사용. 옛 Vertex Express(AQ.) 키는 prepayment credits 소진으로 교체. 키는 HF Space의 LLM_API_KEY(ai-service만 사용, 백엔드엔 없음).
- 보안 TODO: AI_SERVICE_TOKEN 채팅에 2회 노출 → 재발급 필요(3곳 동기화: Secret Manager readmind-ai-service-token + HF Space AI_SERVICE_TOKEN + 로컬 .env). HF_TOKEN은 이미 폐기·재발급 완료.
- 로컬 웹 실행: cd web; npm run dev (사내 MITM으로 프록시 TLS 깨지면 $env:NODE_EXTRA_CA_CERTS="C:\dayeong\99.etc\ReadMind\ai-service\certs\corp-root.crt"). R2 CORS는 http://localhost:5173 적용됨(GET/PUT).
- Cloud Shell에서 Space 재배포: export HF_TOKEN=<새키>; bash deploy/hf-space-deploy.sh. 진단: bash deploy/diag-ai-parse-pdf.sh ~/LoRA_3p.pdf pdf (AI_SERVICE_TOKEN export 필요).
- feature_list: 변경 없음. p0-beta-validate 아직 false(브라우저 e2e 미완).

[2026-07-03] 브라우저 업로드 FAILED 원인 확정 + 파싱 견고화 (p0-beta-validate 선행).
- 원인 확정: 브라우저와 100% 동일한 시퀀스(create→R2 PUT Content-Type:application/pdf→complete)를 PowerShell로 Cloud Run에 재현 → id=30 정상 READY(10초). 즉 어제 id=27 실패는 코드 결함이 아니라 **Space 재배포/콜드스타트 창의 일시 5xx를 AiParseClient가 재시도 없이 즉시 FAILED 처리**한 것(어제 Space 2회 재배포와 시간대 일치). presign은 content-type 미서명이라 브라우저 PUT 헤더는 무관, PUT 실패 시 complete 자체가 안 가므로(PENDING 잔류) "FAILED=업로드는 성공" 확정.
- 견고화 구현(명세서 §3 documents.parse_error / §4.2 먼저 갱신 후 코드):
  (1) AiParseClient 일시오류(5xx/429/타임아웃/연결) 백오프 재시도 — Retry.kt retryTransient, 기본 4회(5s→10s→20s), env AI_PARSE_RETRY_MAX_ATTEMPTS/AI_PARSE_RETRY_BACKOFF. 4xx는 즉시 실패.
  (2) 미사용이던 parseTimeoutSeconds를 실제 readTimeout으로 연결 + connect timeout 10s (JDK HttpClient 기본 무한대기 차단).
  (3) V2__documents_parse_error.sql + Document/DTO/러너 — FAILED 시 예외요약(500자 절단) 저장, 성공·재complete 시 NULL. API 노출(NON_NULL이라 FAILED일 때만 내려감). shared DocumentDto.parseError?.
  (4) 웹: 서재 FAILED 카드에 사유 표시 + "다시 분석" 버튼(useRetryParse=complete 재호출. complete는 READY만 스킵이라 기존 계약 그대로).
- 검증(라이브): gradlew test(RetryTest 5 + 러너 parse_error 3 포함) + integrationTest(실 PG에 V2 적용·부팅) + npm typecheck/build/vitest 15. **런타임 e2e: mock AI(503↔200 전환)+bootRun+실 MinIO로 3시나리오** — flaky:2→재시도 3회만에 READY / fail→정확히 4회 시도 후 FAILED+parseError="ServiceUnavailable: 503..." 목록·단건 노출 / ok로 전환 후 complete 재호출→READY+parseError 소거. READY 재complete는 AI 호출 0건(캐싱 유지).
- 남은 것(사용자): Cloud Run 재배포(gcloud, Cloud Shell) → 브라우저 업로드 최종 확인 → p0-beta-validate. AI_SERVICE_TOKEN 재발급 TODO 여전.

[2026-07-03 오후] **진짜 원인 확정 + 수정 — "브라우저만 실패"는 콜드스타트가 아니라 vite dev 프록시 오라우팅이었다.** (오전의 콜드스타트 가설은 오진으로 정정)
- 재배포 후에도 사용자 브라우저 업로드 FAILED(id=31), 단 parse_error=NULL = 구코드가 처리했다는 뜻. 판별 근거 3종:
  (1) Neon 직접 조회(psycopg, 호스트에서 sslmode=require라 MITM 무관): 사용자 계정 업로드는 어제오늘 7건 전부 FAILED, 내 재현은 전부 READY. R2 객체 바이트 검사 — 실패 문서들도 정상 PDF(브라우저/Fasoo 변조 아님).
  (2) HF Space run 로그(huggingface.co API /logs/run SSE, PowerShell+httpx로 조회 가능): **사용자 업로드 시각에 /ai/parse 요청 자체가 Space에 안 옴**. 내 재현/CLI/프로브는 전부 기록됨(오늘 프로브의 502×4 재시도까지 보임).
  (3) 로컬 readmind-backend 컨테이너 로그에 id=31 파싱 실패 스택 발견: 로컬 ai-service → Gemini **429 RESOURCE_EXHAUSTED(옛 AQ. Vertex 키, 크레딧 소진)**.
- 메커니즘: vite.config.ts가 `process.env.VITE_DEV_API_TARGET`을 읽음 → **.env.local은 config 자신의 process.env에 주입되지 않음**(import.meta.env 전용) → 프록시가 조용히 기본값 localhost:8080=로컬 docker 백엔드(구 이미지)로 감 → 로컬 ai-service의 소진된 키로 즉시 429 → FAILED. 두 백엔드가 같은 Neon을 공유해 한 서재에 섞여 보여 혼선 가중. CLI/diag는 Cloud Run 직행이라 성공 — "브라우저만 실패"의 전부가 이것.
- 수정: vite.config.ts를 defineConfig(({mode}) => …) + `loadEnv`로 전환(commit). 검증(라이브): dev 프록시 응답 헤더 `server: Google Frontend`+`x-cloud-trace-context` 확인 + 프록시 경유 업로드 e2e READY(id=34, 10s). typecheck+vitest 15 통과.
- 재발 방지: 로컬 앱 컨테이너 4종(backend/web/caddy/ai-service) stop — 같은 Neon/R2에 쓰는 구버전+깨진 키 스택이 함정으로 남지 않게. 복구는 `docker start readmind-backend readmind-web readmind-caddy readmind-ai-service`. 로컬 스택 다시 쓰려면 .env LLM_API_KEY를 새 AIza 키로 교체 필요.
- 오전 견고화는 유효 확인: 프로브(id=33, 업로드 생략 의도적 실패)에서 새 리비전이 재시도 4회(~42s) 후 FAILED + parse_error에 Space 502 detail(S3 NoSuchKey) 저장·API 노출 실동작.
- 남음: 사용자 브라우저 최종 확인(npm run dev 재시작 후 업로드)만. 이후 p0-beta-validate.
- **[사용자 확인 완료] 브라우저 e2e 성공** — loadEnv 수정 후 실 브라우저에서 LoRA_3p.pdf 업로드→READY→요약/Q&A 정상("오대박 너무잘돼"). Phase 0 가치 경로(업로드→요약→Q&A→근거)가 실사용자 브라우저에서 최초로 완주됨. 이제 p0-beta-validate(베타 배포+재사용률 계측)만 남음.

[2026-07-04] 하네스 엔지니어링 리팩토링 (집 PC 최초 세션 — 어떤 모델로 작업해도 동일 품질이 나오게 판단→결정론적 게이트로 이관)
- 완료(검증됨): 훅 전면 재작성 + 테스트 14케이스 전부 PASS + 실패경로 라이브 확인.
  - **SessionStart 신규**(.claude/hooks/session-start.sh): 세션 시작 시 진행로그 최신 항목·미완료 feature·git 상태·검증도구 가용성을 자동 주입. 도구 누락 시 "검증 게이트 비활성" 경고(조용한 no-op 방지).
  - **guard.sh→guard.py**: raw grep→훅 JSON 필드 검사로 오탐 제거. 시크릿 패턴 확장(sk-/AIza/hf_/ghp_/AKIA/xox/AQ./private key/jwt). **.txt 생성 차단 신설**(Write/Edit/Bash리다이렉트/PS Out-File·Set-Content, requirements*.txt 예외 — DRM 규칙 기계화). PowerShell 툴도 matcher에 추가. stderr UTF-8 강제(cp949 깨짐 수정).
  - **verify.sh→verify.py**: (구버전은 항상 exit 0 = 장식용이었음) 편집 파일 경로 기반 모듈 디스패치 — ai-service *.py→ruff(파일 단위, venv 우선), web/packages *.ts(x)→npm run typecheck. **실패 시 exit 2로 에러가 모델에 즉시 피드백**. 도구 없으면 조용히 통과(SessionStart가 이미 경고). backend *.kt는 편집 시점 검사 없음 → /evening에서 gradlew test 의무화.
  - 구 .claude/skills/verify-*/check.sh 삭제(디스패치가 verify.py로 통합).
  - **/morning·/evening 수정**: claude-progress.txt 참조 버그→.md. evening에 모듈별 검증 명령 명시(실행 출력이 근거) + 진행로그 템플릿 고정. morning에 spec 필드→명세서 확인 단계 추가.
  - CLAUDE.md §12.5 갱신(훅 3종 체계 + "모델 판단 대신 결정론적 게이트" 원칙 명문화). settings.local.json allow 확대(git 조회/npm 검증/ruff/pytest/gradlew — 권한 프롬프트 감소).
  - 집 PC 부트스트랩: 루트 npm install(워크스페이스) + pip ruff. 검증: guard 14케이스 스크립트 PASS, verify 깨진 py→ruff 에러 exit 2 피드백 확인, App.tsx→typecheck 통과 확인.
- ai-service/.venv 구축 완료(집, Python 3.14): 전체 wheel 정상(PyMuPDF 1.28 포함), **pytest 122 passed/6 skipped** — Fasoo 우회 env 없이 통과. verify.py가 venv ruff를 자동 사용, SessionStart "게이트 비활성" 경고 소멸(전 게이트 활성). session-start.sh 한글 cp949 깨짐 수정(PYTHONIOENCODING=utf-8).
- 집 PC 전체 그린 베이스라인 확정: ai-service pytest 122 pass / web vitest 15 pass / backend gradlew test 80 pass(첫 실행 3m32s, 이후 데몬 캐시). 세 모듈 모두 이 머신에서 빌드·테스트 재현 확인.
- 막힘: 없음. backend 편집 시점 게이트 없음(gradle 속도 문제, 의도된 트레이드오프 — 커밋 전 gradlew test).
- 다음 먼저 할 것: feature_list 재개 — active_phase 1의 web-reader-settings(또는 p0-beta-validate 실사용 계측). 원하면 ai-service venv 구축.
- 참고 컨텍스트: 근거 리서치=Anthropic 공식 베스트프랙티스(hooks/memory/skills 문서). 훅 테스트 스크립트는 세션 스크래치패드(레포 밖). 회사↔집 메모리 동기화 완료(~/.claude/projects/D--dy-ReadMind/memory).

[2026-07-05] p0-beta-validate 선행 작업 — HF Space /docs 비공개 + 재사용률 계측 러너 + 훅 침묵버그 2건 수정 (커뮤니티 공유는 사용자가 보류)
- 완료(검증됨):
  - **HF Space /docs·/redoc·/openapi.json 비공개**(7c3bee2, TODO 2026-07-01 이행): main.py FastAPI에 docs/redoc/openapi=None + 노출표면 테스트 2개. Space 재배포(31eb127→4561f85) 후 라이브 검증 — /docs·/openapi.json·/redoc=404, /health=200, /ai/qa 무토큰=401. **재배포 후 클라우드 e2e PASS**(docId=38: parse 16s→READY→한국어 요약→qa sources 포함).
  - **재사용률 계측 러너**(90149a8): scripts/metrics/run_reuse_rate.py — reuse_rate.sql을 POSTGRES_URL(Neon)에 read_only 트랜잭션으로 원커맨드 실행. 라이브: uploaders=18(전원 e2e 테스트 계정·각 1건), 재사용률 0%(베타 전 당연), 활성화율 22.2%. **베타 시작 전 e2e 계정(@readmind.dev) 제외 필터 추가 고려**.
  - **훅 침묵 버그 2건**(a8cb116): ① verify.py — subprocess cp949 디코드 크래시로 lint 실패가 exit 1 침묵 통과(utf-8+replace로 수정, main.py E501을 실제로 놓쳤던 사례로 발견) ② guard.py — 커밋 메시지 안 ".env" 언급 오탐(따옴표 내 문자열 제거 후 검사).
  - hf-space-deploy.sh 개선(5e99697): DEPLOY_MSG 파라미터화 + __pycache__ 오염 방지(이번에 pyc가 Space에 커밋됐던 것 정리 푸시로 제거).
- 환경 특이점(집 PC): GFE(*.run.app)가 Windows curl의 빈 POST(Content-Length 없음)를 411 거부 — bash e2e 스크립트가 complete 단계에서 깨짐. python httpx로는 정상. jq도 미설치. → **집에서 클라우드 e2e는 python(스크래치패드 e2e_cloud.py 참조)으로**.
- 사용자 지시: 커뮤니티 공유는 보류. **확인 질문 없이 진행**(메모리 proceed-without-asking).
- 남음(p0-beta-validate): ① AI_SERVICE_TOKEN 재발급(사용자 콘솔 3곳: Secret Manager+HF Space Settings+로컬 .env) ② 웹 프론트 정적 배포 ③ 커뮤니티 공유+재사용률 측정(보류 중). feature passes:false 유지.
