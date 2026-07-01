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

[다음 세션 시작 시]
- 막힘: 없음. Phase1 진행 중(be-annotations-crud, be-highlight-search 완료).
- 다음 후보(Phase1, passes:false 위에서부터): ai-parse-multi(M1, EPUB/ebooklib·TXT·DOCX/python-docx 파서 추가, 디스패처 format→parser) → web-reader-settings(M3) → ai-translate(M1) → mobile-reader-sync(M4).
- 운영: 스택 up 상태(docker compose --profile app, override로 ai 8000 미퍼블리시). 라이브 검증은 docker exec(MSYS_NO_PATHCONV=1). backend/ai 코드 바꾸면 해당 이미지 재빌드+force-recreate 필요. 브라우저 실사용은 R2 CORS 필요(현재 프리플라이트 403, 미반영/전파대기 — 사용 직전 재확인). Phase 0 개발(M1+M2+M3) + 배포 컨테이너화까지 완료. 남은 phase0 항목은 p0-beta-validate 하나(코드 아님: 실 배포+재사용률 계측).
- 후보 A(p0-beta-validate): LLM_API_KEY 실값 세팅 → `docker compose --profile app up`으로 실 PDF 업로드→파싱→요약→QA 근거점프 브라우저 e2e 1회 → 커뮤니티 베타 배포 + 재사용률 지표(같은 유저가 다른 논문 또 업로드). 배포 대상/호스팅은 사용자 결정 필요.
- 후보 B(Phase1 진입, active_phase 0→1): be-annotations-crud(§4.3, 하이라이트/메모/북마크/진행률 CRUD, location JSONB, 소유권 검증).
- 환경: 진행 로그는 .md. 웹 web/ npm run {typecheck,build,test}. 백엔드 backend/ ./gradlew. 풀스택 `docker compose --profile app up -d`(단, 호스트 8000 충돌 시 ai-service 포트 override). docker readmind-postgres:5432.
