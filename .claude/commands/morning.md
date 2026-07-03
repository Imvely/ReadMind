어제 작업을 이어서 한다. CLAUDE.md §12.1 세션 시작 루틴을 따른다.
(SessionStart 훅이 진행로그 요약·미완료 항목·git 상태를 이미 주입했다 — 그걸 출발점으로 쓰되, 아래를 직접 재확인한다.)

1. claude-progress.md 의 마지막 [날짜] 블록 전체를 읽어 직전 상태(완료/막힘/다음 할 일)를 파악한다. (.txt 아님 — .md만 사용)
2. `git log --oneline -10` 으로 최근 커밋을 확인한다.
3. feature_list.json 에서 active_phase 의 `passes:false` 중 **위에서부터 하나만** 고른다. Phase·모듈 순서(M1→M2→M3→M4) 건너뛰기 금지.
4. 고른 항목의 `spec` 필드가 가리키는 명세서 섹션(docs/ReadMind_개발명세서_BuildSpec.md)을 읽어 입출력 계약을 확정한다.
5. 검증 게이트 상태를 확인한다: SessionStart 훅이 "검증 게이트 일부 비활성" 경고를 냈다면 코드 작업 전에 먼저 해소한다(npm install / pip install ruff / venv).

그다음, 코드를 바로 작성하지 말고 아래를 먼저 보고한다:
- 어제 어디까지 됐는지
- 지금 막혀있는 것이 있으면 무엇인지
- 오늘 처음 손댈 작업 한 가지 (feature id + 명세서 섹션)
내가 확인하면 그때 시작한다.
