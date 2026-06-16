# ReadMind 새 PC 초기 세팅 가이드 (Windows 기준)

> 목표: **이 문서를 위에서 아래로 그대로 따라 하면, 원본 PC와 동일한 개발 환경**(Docker 인프라 · DB · Claude Code hooks/설정)이 된다.
> 전제: 코드는 이미 git에 올라가 있고, 새 PC에서 `git clone` / `git pull`로 받은 상태에서 시작한다.
>
> ⚠️ git에 올라오지 **않는** 것이 2개 있다(설계상 의도). 이 가이드의 **2단계(.env)**, **6단계(settings.local.json)**에서 직접 만든다.
> - `.env` — 시크릿이라 `.gitignore` 처리 (`.env.example`만 커밋됨)
> - `.claude/settings.local.json` — 머신별 권한/로컬 설정이라 `.gitignore` 처리

---

## 0. 사전 설치 (한 번만)

아래 4개를 설치한다. 버전은 원본 PC 기준이며, 같거나 상위면 된다.

| 소프트웨어 | 원본 PC 버전 | 비고 |
|---|---|---|
| **WSL2 + Ubuntu** | — | PowerShell 관리자: `wsl --install` 후 재부팅 |
| **Docker Desktop** (Windows) | Docker 29.3.1 / Compose v5.1.1 | 설치 후 실행해 두기 |
| **Node.js** | v22.x (npm 10.x) | WSL 안에 설치 권장(아래 참고) |
| **Claude Code** | 최신 | `npm i -g @anthropic-ai/claude-code` |

> **Node는 WSL 안에 설치**한다(Windows용 node 아님). WSL 터미널에서:
> ```bash
> curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt-get install -y nodejs
> ```
> postgres MCP가 `npx`로 뜨고, hook의 `tsc` 검사도 node를 쓰므로 필요하다.

### 0-1. ⚠️ 가장 중요 — 이 프로젝트는 "WSL 안에서" 연다

코드가 `C:\...`(예: `C:\dayeong\99.etc\ReadMind`)에 있어도, **모든 명령은 WSL 터미널에서** 실행한다.
Claude Code도 WSL에서 띄운다. hook이 `bash` 스크립트라서 그렇다.

WSL에서 프로젝트로 이동:
```bash
cd /mnt/c/dayeong/99.etc/ReadMind      # 본인 clone 경로에 맞게
```

### 0-2. Docker를 WSL에서 부르는 방법 (둘 중 택1 — 결과는 같다)

이 저장소의 `docker compose` 명령은 WSL에서 실행해야 한다. 두 방법 중 하나를 쓴다.

- **방법 A (권장, 가장 간단):** Docker Desktop → Settings → **Resources → WSL Integration** → 본인 배포판 토글 ON.
  그러면 WSL에서 그냥 `docker`, `docker compose`가 동작한다.
- **방법 B (원본 PC 방식, 통합 OFF일 때):** 풀패스 별칭을 건다. WSL 터미널에서:
  ```bash
  echo "alias docker='/mnt/c/Program\ Files/Docker/Docker/resources/bin/docker.exe'" >> ~/.bashrc
  source ~/.bashrc
  ```
  이후 이 가이드의 `docker ...` 명령을 그대로 쓰면 된다.

> 확인: `docker --version` → `Docker version 29.x` 가 나오면 OK.

---

## 1. 저장소 받기

```bash
git clone <repo-url> /mnt/c/dayeong/99.etc/ReadMind   # 경로는 자유. 단 C 드라이브 권장
cd /mnt/c/dayeong/99.etc/ReadMind
```

---

## 2. `.env` 만들기 (git에 없음 — 직접 생성)

아래 블록을 **그대로** 복사해 `.env`로 저장하면 원본 PC와 동일하게 동작한다.
(로컬 개발용 값이다. 운영 비밀이 아니다. 그래도 `.env`는 절대 커밋 금지 — guard hook이 막는다.)

```bash
cat > .env <<'EOF'
# ===== DB (PostgreSQL 16 + pgvector) =====
POSTGRES_USER=readmind
POSTGRES_PASSWORD=readmind
POSTGRES_DB=readmind
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
# 컨테이너 내부 앱이 쓰는 URL (호스트명 = 서비스명 postgres)
POSTGRES_URL=postgresql://readmind:readmind@postgres:5432/readmind
# 호스트(=postgres MCP, IDE)가 쓰는 URL (localhost)
READMIND_DATABASE_URL=postgresql://readmind:readmind@localhost:5432/readmind

# ===== Redis 7 =====
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_URL=redis://redis:6379/0

# ===== S3 호환 스토리지 (MinIO) =====
S3_ENDPOINT=http://minio:9000
S3_BUCKET=readmind
S3_KEY=minioadmin
S3_SECRET=minioadmin123
S3_REGION=us-east-1
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin123

# ===== auth (JWT) — 로컬 개발용 더미 =====
JWT_SECRET=local-dev-jwt-secret-please-change-32+chars
JWT_ACCESS_TTL=900
JWT_REFRESH_TTL=1209600

# ===== service-to-service =====
AI_SERVICE_URL=http://ai-service:8000
AI_SERVICE_TOKEN=local-dev-service-token

# ===== LLM (provider 추상화) — 실제 키는 각자 채움 =====
LLM_PROVIDER=commercial
LLM_MODEL=
LLM_API_BASE=
LLM_API_KEY=
EMBEDDING_MODEL=
EMBEDDING_DIM=1024

# ===== billing =====
BILLING_PROVIDER=toss
BILLING_API_KEY=
BILLING_WEBHOOK_SECRET=

# ===== quota (free tier) =====
FREE_SUMMARY_PER_MONTH=10
FREE_QA_PER_MONTH=20
FREE_TRANSLATE_PER_MONTH=10
FREE_STORAGE_MB=200
EOF
echo ".env 생성 완료"
```

> **`POSTGRES_PASSWORD`/`POSTGRES_USER`/`POSTGRES_DB`** 와 **`READMIND_DATABASE_URL`의 비밀번호는 반드시 일치**해야 한다(위 블록은 모두 `readmind`로 맞춰져 있다). 어긋나면 `password authentication failed`가 난다.
> `LLM_API_KEY` 등 실제 외부 API 키는 각 PC에서 본인 것을 채운다(원본 PC에도 비어 있을 수 있음).

---

## 3. 포트 충돌 확인 (특히 5432)

다른 프로젝트의 Postgres가 이미 5432를 쓰고 있으면 ReadMind DB가 안 뜬다.

```bash
docker ps --format '{{.Names}}\t{{.Ports}}' | grep 5432
```

- 아무것도 안 나오면 → 그대로 4단계 진행.
- 다른 컨테이너가 5432를 점유 중이면 → **그 컨테이너를 멈추거나**(`docker stop <이름>`), `.env`의 `POSTGRES_PORT`를 `5433` 등으로 바꾸고 `READMIND_DATABASE_URL`의 포트도 `5433`으로 맞춘다.

---

## 4. 인프라 기동 (Docker)

```bash
docker compose up -d        # postgres + redis + minio + 버킷 생성
```

상태 확인 (postgres/redis/minio 모두 `healthy`가 될 때까지 10~20초):
```bash
docker compose ps
```

기대 결과: `readmind-postgres`, `readmind-redis`, `readmind-minio` 가 `Up (healthy)`,
`readmind-createbuckets` 는 버킷 생성 후 종료(정상).

> 앱(ai-service/backend)은 아직 띄우지 않는다. Dockerfile이 생기는 Phase에서 `docker compose --profile app up -d`로 추가한다.

---

## 5. DB 연결 검증

pgvector 확장이 등록됐는지 확인(`infra/postgres/init/01-extensions.sql`이 최초 1회 자동 실행됨):

```bash
docker exec readmind-postgres psql -U readmind -d readmind -c "\dx"
```

`vector` 확장이 목록에 보이면 성공. (테이블은 아직 0개가 정상 — 스키마는 backend Flyway 마이그레이션이 만든다.)

---

## 6. Claude Code 설정 복원

git으로 따라오는 것 / 직접 만들 것을 구분한다.

### 6-1. 따라오는 것 (확인만)
- `.claude/settings.json` — **hooks 정의** (PreToolUse=guard, PostToolUse=verify)
- `.claude/hooks/guard.sh`, `verify.sh` — hook 실제 스크립트 (repo에 포함, 경로는 `$CLAUDE_PROJECT_DIR` 기준이라 PC 무관)
- `.claude/skills/verify-python/`, `verify-web/` — verify 디스패처가 호출하는 검사
- `.claude/commands/morning.md`, `evening.md` — 세션 시작/종료 슬래시 커맨드
- `.mcp.json` — postgres MCP 정의

> hook 스크립트는 `bash <파일>`로 실행되므로 실행권한(+x)이 없어도 동작한다(DrvFs에서 chmod가 안 먹는 문제 회피).

### 6-2. 직접 만들 것 — `.claude/settings.local.json` (git에 없음)

이 파일이 있어야 **postgres MCP가 자동 승인**되고, 자주 쓰는 명령의 권한 프롬프트가 줄어든다.

```bash
cat > .claude/settings.local.json <<'EOF'
{
  "permissions": {
    "allow": [
      "Bash(docker --version)",
      "Bash(docker compose *)",
      "Bash(docker ps *)",
      "mcp__postgres__query"
    ]
  },
  "enableAllProjectMcpServers": true,
  "enabledMcpjsonServers": [
    "postgres"
  ]
}
EOF
echo "settings.local.json 생성 완료"
```

### 6-3. (선택) 사용자 전역 hook 폴백
원본 PC에는 `~/.claude/guard.sh`, `~/.claude/verify.sh`가 전역 폴백으로도 남아 있다.
**이 프로젝트는 repo 안의 `.claude/hooks/`를 쓰므로 새 PC에 필수는 아니다.**
다른 프로젝트에서도 같은 가드를 쓰고 싶으면 원본 PC의 두 파일을 `~/.claude/`로 복사해 둔다.

---

## 7. 최종 검증 (Claude Code에서)

WSL에서 프로젝트 폴더에 들어가 `claude` 실행 후:

1. **MCP 연결**: `claude mcp list` → `postgres ... ✔ Connected`
2. **DB 쿼리**: postgres MCP로
   ```sql
   SELECT current_database(), current_user, (SELECT string_agg(extname,', ') FROM pg_extension);
   ```
   → `readmind / readmind / plpgsql, vector` 가 나오면 인프라+MCP 정상.
3. **hook 동작**: 아무 파일이나 Edit해보면 PostToolUse(verify)가 도는지 확인. 시크릿 문자열을 커밋/작성 시도하면 guard가 차단하면 정상.

세 가지가 모두 통과하면 원본 PC와 동일한 환경이다.

---

## 부록 — 자주 막히는 곳

| 증상 | 원인 / 해결 |
|---|---|
| `password authentication failed for user "readmind"` | 5432를 다른 프로젝트 DB가 점유 중. 3단계 참고(중지 또는 포트 변경). |
| MCP가 `✔ Connected`인데 쿼리만 실패 | 위와 동일. health check는 TCP만 보고, 인증은 쿼리 때 일어난다. |
| `MINIO_ROOT_USER variable is not set` 경고 | `.env` 누락/미작성. 2단계 다시. |
| `docker: command not found` (WSL) | 0-2단계. WSL Integration 켜거나 docker.exe 별칭. |
| hook이 안 걸림 | WSL이 아니라 Windows 셸에서 claude를 띄웠을 때. 반드시 WSL에서 실행. |
| `git commit`이 `Operation not permitted` | `/mnt/c`(DrvFs)에서 git lock chmod 불가. 커밋은 Windows Git/IDE에서 수행. |
