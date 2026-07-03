오늘 작업을 마무리한다. CLAUDE.md §12.4 세션 종료 루틴을 따른다.
**"괜찮아 보인다"로 판단하지 말고, 아래 검증 명령을 실제로 실행해 그 출력을 근거로 삼는다(§12.3).**

1. 검증 명령 실행 — 오늘 손댄 모듈만:
   - web/packages 를 건드렸으면: `npm run typecheck` + `npm test`
   - ai-service 를 건드렸으면: `ruff check ai-service` + (venv 있으면) pytest
   - backend 를 건드렸으면: `cd backend && ./gradlew test` (편집 시점 훅이 Kotlin은 검사하지 않으므로 여기서 반드시)
   하나라도 실패하면 **커밋하지 말고** 고친 뒤 재실행한다.
2. feature_list.json — §12.3 완료 기준(계약 준수 + 해피패스/실패 테스트 + 빌드/린트/타입 통과 + AI 항목은 sources 실포함)을 **모두** 충족한 항목만 `passes:true`. 절반짜리는 false 유지.
3. `git status --short` 로 커밋 대상 확인 — `.env`·`.txt`·바이너리가 섞였는지 눈으로 본 뒤 `git commit` (메시지 규약 §9).
4. claude-progress.md 에 아래 템플릿으로 추가한다 (.txt 금지):
   ```
   [YYYY-MM-DD] <feature-id 또는 작업 한 줄>
   - 완료(검증됨): <무엇을, 어떤 명령/출력으로 확인했는지>
   - 막힘: <파일:줄 까지 구체적으로 / 없으면 "없음">
   - 다음 먼저 할 것: <한 가지>
   - 참고 컨텍스트: <샘플 경로, 임시 결정, env 등>
   ```
5. 모듈이 끝났으면 해당 README 와 명세서 §8 체크박스도 갱신한다.
