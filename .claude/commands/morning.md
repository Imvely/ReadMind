어제 작업을 이어서 한다. CLAUDE.md §12.1 세션 시작 루틴을 따른다:
1. claude-progress.txt 를 읽어 직전 상태(완료/막힘/다음 할 일)를 파악한다.
2. git log --oneline -10 으로 최근 커밋을 확인한다.
3. feature_list.json 에서 active_phase의 passes:false 중 작업할 항목을 정한다.

그다음, 코드를 바로 작성하지 말고 아래를 먼저 보고한다:
- 어제 어디까지 됐는지
- 지금 막혀있는 것이 있으면 무엇인지
- 오늘 처음 손댈 작업 한 가지
내가 확인하면 그때 시작한다.
