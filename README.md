# ReadMind (가제)

논문·문서·전자책을 읽으면 AI가 자동으로 **요약·복습카드·Q&A**를 만들어주는 웹+안드로이드 동기화 학습 리더.

- 읽기는 무료, 이해(AI)와 기억(복습)은 유료.
- 단일 진실원천(SSOT): [`docs/ReadMind_개발명세서_BuildSpec.md`](docs/ReadMind_개발명세서_BuildSpec.md)
- 작업 규칙: [`CLAUDE.md`](CLAUDE.md)
- **새 PC 초기 세팅(Windows)**: [`docs/SETUP_Windows.md`](docs/SETUP_Windows.md) — git pull 후 따라 하면 동일 환경(Docker/DB/hooks/Claude 설정) 복원

## 프로젝트 구조

```
readmind/
├── docker-compose.yml   # postgres(pgvector) + redis + minio (+ app 프로파일: ai-service/backend)
├── .env.example         # 환경변수 예시 (.env 는 커밋 금지)
├── infra/postgres/init/ # DB 최초 기동 초기화(pgvector 확장)
├── ai-service/          # M1 Python/FastAPI   (Phase 0)
├── backend/             # M2 Spring Boot/Kotlin
├── web/                 # M3 React/TS
├── mobile/              # M4 React Native      (Phase 1~)
└── packages/shared/     # 공유 타입(API 계약)·동기화 로직
```

## 로컬 인프라 기동 (Phase 0)

전제: Docker / Docker Compose v2.

```bash
# 1) 환경변수 준비 — 플레이스홀더(change-me-*)를 실제 값으로 교체
cp .env.example .env

# 2) 인프라 기동 (postgres + redis + minio + 버킷 생성)
docker compose up -d

# 3) 상태 확인 (postgres/redis/minio 가 healthy 여야 함)
docker compose ps
```

접속 정보(기본값):

| 서비스 | 주소 | 비고 |
|---|---|---|
| PostgreSQL | `localhost:5432` | pgvector 확장 사전 등록(`vector`) |
| Redis | `localhost:6379` | |
| MinIO API | `localhost:9000` | S3 호환. 버킷 `readmind` 자동 생성 |
| MinIO 콘솔 | `localhost:9001` | `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` 로 로그인 |

애플리케이션(ai-service/backend)은 각 `Dockerfile` 생성 후 함께 기동:

```bash
docker compose --profile app up -d
```

정리:

```bash
docker compose down        # 컨테이너 중지/삭제 (볼륨 유지)
docker compose down -v      # 볼륨까지 삭제 (데이터 초기화)
```

## 빌드 순서

명세서 §8 Phase를 순서대로: **M1 AI 서비스 → M2 백엔드 → M3 웹 → M4 안드로이드**.
현재 진행 상태는 [`feature_list.json`](feature_list.json) / [`claude-progress.txt`](claude-progress.txt) 참고.
