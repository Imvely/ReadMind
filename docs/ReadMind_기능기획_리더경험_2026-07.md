# ReadMind 기능 기획 — 리더 경험 완성 (2026-07-05)

> 시장조사(일반 이북 리더 8종 + 논문/AI 리더 17종, 웹 리서치 2026-07) 기반 기획.
> 명세서(BuildSpec) 반영 전의 **제안 문서**다 — 채택 항목은 명세서 §3/§4/§6/§8에 먼저 반영 후 구현한다(CLAUDE.md §3).
> 원칙 유지: 기능 백화점 금지. "안 할 것" 목록을 명시한다.

---

## 1. 시장조사 핵심 요약

### 1.1 "사실상 표준" — 없으면 결격인 기능
일반 리더(Kindle/Kobo/Apple/Play북스/리디/밀리/Moon+/KOReader)와 논문 리더(Zotero/Mendeley/Paperpile/Readwise/MarginNote 등) 전반에서 공통 확인:

| # | 기능 | 근거 |
|---|---|---|
| S1 | **복수 색상 하이라이트 + 메모 첨부** | 8개 일반 리더 전부. 컬러 기기 기준 4~5색이 표준. 논문 리더는 좌표 기반 저장+클릭 점프까지 |
| S2 | **북마크** | 전 리더 공통 |
| S3 | **읽던 위치 자동 이어읽기** | Kindle Whispersync가 만든 기대치. Apple/Google/Kobo/리디/밀리 전부 계정 자동 동기화. MobileRead 포럼 최상위 반복 요구 |
| S4 | **주석의 기기 간 동기화** | 위치만 되고 주석이 안 되면 불만(밀리 "필기 수동 동기화" 실제 지적 사례) |
| S5 | **주석 모아보기(독서노트) + 주석 검색** | Kindle Notebook, 리디 독서노트, Zotero 7 "주석 DB화 검색"이 핵심 소구 |
| S6 | **AI 답변의 근거 표시** | AI 리더 진영 예외 없음(SciSpace 좌표 점프/NotebookLM 소스 인용/Elicit 인용구). ChatPDF는 "인용 근사치"로 감점 — **근거 없는 AI는 시장에서 실격**. ✅ 우리 §3 원칙과 일치 |
| S7 | 본문 검색, 목차, 진행률 표시 | 전 리더 공통 |
| S8 | **Markdown/Obsidian·Notion 내보내기(원문 역링크 포함)** | 논문 리더 진영 표준. "리더→Obsidian 파이프"가 PhD 워크플로 콘텐츠의 대부분 |

### 1.2 "감동 기능" — 팬을 만드는 것
- **주석 내보내기/공유**: 가장 뚜렷한 미충족 수요. Apple Books가 내보내기 제거하자 이탈 스레드 다수, 리디·밀리 공식 내보내기 없음(커뮤니티가 우회법 자작)
- **간격반복 복습**: Kindle/KOReader만, 그것도 **'단어' 수준**. 내용 이해 카드는 Readwise Mastery(비논문)·MarginNote(Apple 전용)뿐
- X-Ray(용어/인물 참조), 독서 통계·목표, 고품질 AI TTS, 오디오↔텍스트 교차 이어듣기

### 1.3 빈틈 = ReadMind의 자리 (조사로 실측된 기회)
1. **"좌표 주석 × AI 근거 × 간격반복(FSRS)" 3결합이 시장에 없다.** Zotero(주석만)/NotebookLM(AI만, 리더·스케줄러 없음)/Readwise(3개 있으나 논문 특화 아님)/MarginNote(근접하나 Apple 전용). 이 방향이던 Polar는 저장소 아카이브+도메인 방치로 사망 — 수요 대비 공급 공백의 증거
2. **한국어 논문 × 개인 PDF**: DBpia AI는 자사 DB 뷰어에 갇힘. 국내 대학원생용 "내 PDF" 도구는 전부 해외 서비스
3. **모바일 복습**: 복습은 자투리 시간 활동인데 연구 도구는 전부 데스크톱 중심 → 우리 웹+안드 구도와 부합
4. **과금 신뢰**: SciSpace 크레딧 불만 집중 사례 → 예측 가능한 쿼터가 차별화 (우리 쿼터 모델과 일치)
5. **NotebookLM 공개 노트북(2025.6)**: "URL 하나로 열람+질문" 공유 모델의 선례 — 요약 공유 기능의 시장 검증

---

## 2. 우리 현황 갭 분석

| 시장 표준 | 백엔드 | 웹 UI | 판정 |
|---|---|---|---|
| S1 하이라이트+메모 | ✅ CRUD+횡단검색 (P1) | ❌ | **UI 부채** — PDF 텍스트 레이어 선행 필요 |
| S2 북마크 | ✅ CRUD | ❌ | UI 부채 |
| S3 이어읽기 | ✅ reading_progress | ❌ 저장·복원 안 함 | **UI 부채, 최저비용·최고효과** |
| S4 주석 동기화 | ✅ /sync (LWW·tombstone, 라이브 검증) | (클라 엔진 완성, 어댑터 대기) | 선행 완료 |
| S5 독서노트+주석 검색 | ✅ /highlights/search | ❌ | UI 부채 |
| S6 AI 근거 | ✅ sources+점프+플래시 | ✅ | **경쟁력 확보** |
| S7 본문 검색/목차 | ❌ | ❌ | 갭 (pdf.js findController로 저비용) |
| S8 MD 내보내기 | ❌ | ❌ | 갭 (P2 제안) |
| 감동: 요약 공유 URL | ❌ | ❌ | 갭 (신규 설계 §6.5) |
| 감동: 내용 복습카드+FSRS | 예정 (P2 ai-flashcards-fsrs) | — | **로드맵이 시장 공백과 정확히 일치 — 자신 있게 진행** |

**결론**: P1에서 백엔드만 만들고 UI를 안 붙인 부채(S1·S2·S3·S5)가 시장 "결격 사항"과 정확히 겹친다. 베타 공유 전에 이걸 갚는 게 신기능보다 우선.

---

## 3. 사용자(제품 오너) 아이디어 5개 평가

| 아이디어 | 시장 근거 | 차별화 여지 | 비용 | 판정 |
|---|---|---|---|---|
| ① 하이라이트 칠하기 | S1 표준(결격 사항) | 색상별 필터+AI 추천 하이라이트(§5.5 기보유)와 결합 | 중(PDF 텍스트 레이어 선행) | **P1.5 채택** |
| ② 북마크 | S2 표준 | — | 소 | P1.5 채택 |
| ③ 이어읽기 | S3 표준(Whispersync 기대치) | — | **극소** | **P1.5 채택, 1순위** |
| ④ 요약 URL 공유 | 감동 기능(미충족 수요 1위=공유/내보내기) + NotebookLM 공개 노트북 선례 | **재사용률(P0 지표)에 직결되는 바이럴 루프** — 받은 사람이 가입 동기 | 중 | **P2 채택(전략 기능)** |
| ⑤ 퀵메모(좌표 핀+접기+문서 내 검색) | S1+S5 표준(Zotero "주석 DB화"가 핵심 소구) | 논문 좌표 기반 접이식 핀은 일반 리더엔 없는 논문 특화 UX | 중(notes 스키마 확장) | **P1.5 채택** |

전부 시장 근거 있음 — 다만 ④는 베타 트래픽이 있어야 의미가 커서 P2로.

---

## 4. 로드맵 제안

### P1.5 "리더 완성" (신설 — 베타 공유 전 결격 해소)
> 순서 근거: 비용↑ 순 + 의존성(하이라이트가 텍스트 레이어 선행)

1. `web-resume-reading` — 이어읽기 (진행률 저장+복원)
2. `web-pdf-textlayer` — PDF 텍스트 레이어(선택 가능) + 본문 검색
3. `web-highlight-ui` — 선택 툴바(하이라이트 색 4종/메모/AI에 질문) + 낙관적 업데이트
4. `web-quick-note` — 좌표 핀 퀵메모(접기/펼치기) + 문서 내 주석 검색
5. `web-annotations-panel` — 우측 패널 "기록" 탭(하이라이트·메모·북마크 모아보기, 클릭 점프) + 북마크 토글

### P2에 추가 (기존 ai-flashcards-fsrs·quota·billing에 +2)
6. `share-summary-url` — 요약 공개 공유 링크
7. `export-markdown` — 하이라이트/메모 MD 내보내기(원문 역링크) — S8 표준이라 승격

### 하지 않을 것 (기능 백화점 방지 — 조사로 확인된 것 포함)
- TTS/오디오북 (리디·밀리의 전장 — 우리 차별화 아님)
- X-Ray, 독서 통계/목표/배지 (감동이지만 코어 아님)
- 서지 관리·인용 양식(BibTeX 등) — Zotero의 영역, 연동으로 풀 것
- 웹 아티클 클리퍼(Readwise 영역), 실시간 협업(P3 기존 항목 유지), 사전(브라우저로 충분)

---

## 5. 상세 설계

### 5.1 이어읽기 `web-resume-reading`
- **저장**: 리더에서 스크롤 정지 2초(디바운스) 시 `PUT /documents/{id}/progress` (§4.3 기존) — `location`: PDF=`{type:"pdf",page,scrollRatio}`, EPUB=`{type:"epub",cfi}`. percent 동봉
- **복원**: 리더 진입 시 GET 진행률 → PDF는 해당 page로 scrollToPage, EPUB은 `rendition.display(cfi)`. "이어읽기: p.12부터 (73%)" 토스트 + '처음부터' 버튼(Apple Books 패턴)
- **서재 카드**: percent 프로그레스 바 표시
- 스키마/계약 변경 없음. sync는 progress 경로로 이미 처리됨

### 5.2 PDF 텍스트 레이어 + 본문 검색 `web-pdf-textlayer`
- pdf.js `TextLayer` 렌더를 캔버스 위에 오버레이(표준 pdf.js 패턴) — 드래그 선택 가능해짐
- 부수 효과: 근거 플래시 하이라이트가 텍스트 레이어 기반으로 더 정밀해질 수 있음(추후)
- 본문 검색: 검색 입력 → `getTextContent` 캐시 기반 페이지별 매치 목록 → 점프(기존 snippetMatch 재사용)
- 성능: 텍스트 레이어는 보이는 페이지 ±2만 마운트(가상화)

### 5.3 하이라이트 UI `web-highlight-ui`
- **선택 툴바**: 텍스트 선택 시 플로팅 툴바(§6.1 기존 스펙) — 색 4종(노랑/초록/파랑/핑크 — 시장 표준 수), 메모 추가, "AI에 질문"(선택 텍스트를 Q&A 입력으로)
- **저장**: `POST /documents/{id}/highlights` (§4.3 기존). location: PDF=`{type:"pdf",page,rects[]}`(선택 영역 rect들), EPUB=`{type:"epub",cfi}` — **스키마 그대로**(§3 location JSONB 설계가 이미 수용)
- **렌더**: 페이지별 하이라이트 오버레이(플래시 하이라이트와 같은 좌표 방식, 영구 표시)
- **낙관적 업데이트**(§6.1): 즉시 표시 → 실패 시 롤백. TanStack mutation
- EPUB: epubjs `rendition.on('selected')` + `annotations.highlight(cfi)`

### 5.4 퀵메모 `web-quick-note` — 스키마 변경 1건
- **V4 마이그레이션 + 명세서 §3 갱신 필요**: `notes.location JSONB` 추가(기존 page_no 유지 — location이 정밀 좌표, page_no는 요약/검색용). shared `PushNote`·`SyncNoteDto`에 location 추가(계약 갱신)
- **UX**: 본문 우클릭(또는 선택 툴바의 '메모') → 그 좌표에 접힌 핀(📌) 생성 → 클릭 시 말풍선 펼침(수정/삭제). 접기 상태가 기본 — 본문 가림 최소화
- **문서 내 주석 검색**: 우측 "기록" 탭 상단 검색창 — 해당 문서의 하이라이트 selected_text/note + 메모 body 통합 필터(클라 필터로 시작, 서버 검색은 횡단검색 API 재사용)
- 논문 특화 포인트: 핀에 "p.3 §2.1 근처" 자동 라벨(페이지+가장 가까운 텍스트 앞부분)

### 5.5 기록 패널 + 북마크 `web-annotations-panel`
- 우측 패널 탭 확장: 요약 / Q&A / **기록**. 기록 탭 = 하이라이트(색 필터)·메모·북마크 시간순 목록, 클릭 시 본문 점프(기존 scrollToPage+플래시 재사용)
- 북마크: 리더 헤더 🔖 토글(현재 페이지/CFI). GET/DELETE는 §4.3 기존 API
- 이것이 시장 표준 "독서노트"(S5)의 우리 버전 — 문서 단위. 전 문서 횡단 뷰(/highlights 페이지)는 P2 유지

### 5.6 요약 공유 URL `share-summary-url` — 신규 설계 (P2)
- **명세서 §3/§4에 신설 필요**:
  - 테이블 `share_links(id, user_id, document_id, token UNIQUE(랜덤 22자+), scope='SUMMARY', revoked_at, created_at)`
  - `POST /documents/{id}/share` → `{url}` (재호출 시 기존 토큰 재사용) / `DELETE /documents/{id}/share`(회수)
  - `GET /public/shared/{token}` — **무인증**, 요약 JSON+문서 제목만 반환(원문 파일·Q&A 접근 불가. 소유권 규칙의 명시적 예외라 명세서에 반드시 기록)
- **공개 페이지**: `/#/s/{token}` — 요약 카드 + "ReadMind로 이 논문 읽기" CTA(가입 유도 = 재사용률 루프). OG 메타로 링크 미리보기
- **남용 방지**: 토큰 무열거(랜덤 128bit+), 회수 가능, 무인증 엔드포인트 rate limit
- NotebookLM 공개 노트북과 차별점: 우리는 요약'만' 공개(원문 비공개) — 저작권 안전

### 5.7 MD 내보내기 `export-markdown` (P2)
- `GET /documents/{id}/annotations/export?format=md` → 하이라이트(색 태그)+메모를 페이지순 MD로. 각 항목에 `readmind://doc/{id}?page=N` 역링크(웹 URL 형태)
- Obsidian/Notion 공식 연동은 후순위 — 파일 다운로드부터(표준 충족의 최소형)

---

## 6. 다음 액션
1. 이 문서 리뷰(제품 오너) → 채택 확정
2. 채택 시: 명세서 §3(notes.location, share_links)·§4(share/export API)·§6.1(기록 탭)·§8(P1.5 체크리스트) 갱신 + feature_list.json 항목 추가
3. 구현 순서: 5.1 → 5.2 → 5.3 → 5.4 → 5.5 (베타 공유 전) / 5.6·5.7은 P2 진입 시

*출처: 세부 URL은 리서치 원문(세션 기록) 참조. 대표 — Kindle 사용자 가이드, Kobo Reading Life, Zotero docs, Readwise Mastery docs, NotebookLM 공식 블로그(공개 노트북·학생 기능), SciSpace Trustpilot, Semantic Reader(CACM), DBpia AI, MobileRead 포럼.*
