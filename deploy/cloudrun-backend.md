# Cloud Run 백엔드 배포 런북 (readmind-backend)

> Spring Boot(Kotlin) 백엔드를 Google Cloud Run에 배포한다.
> **시크릿 값은 이 문서에 절대 넣지 않는다** — 이름과 명령만. 값은 로컬 `.env` 또는 Secret Manager 콘솔에서만.

## 대상
- 프로젝트: `gen-lang-client-0471683922` (번호 `53543020852`)
- 리전: `asia-northeast3` (서울)
- 서비스: `readmind-backend`
- 현재 URL: `https://readmind-backend-53543020852.asia-northeast3.run.app`
- 빌드: `--source=./backend` → Cloud Build가 `backend/Dockerfile`(멀티스테이지 Gradle→JRE)로 빌드.
- 실행 위치: **Cloud Shell**(구글 인프라 → 사내 MITM 무관). `--source`는 CWD 기준이므로 **반드시 `~/ReadMind` 안에서** 실행.

## 백엔드가 읽는 환경변수
`System.getenv()` 미사용. 전부 `backend/src/main/resources/application.yml`의 `${...}`.

### 비민감 → `--set-env-vars`
| 변수 | 값 |
|---|---|
| `AI_SERVICE_URL` | `https://dayeongim-readmind-ai.hf.space` (HF Space) |
| `S3_ENDPOINT` | R2 엔드포인트 URL |
| `S3_BUCKET` | `readmind-dev` |
| `S3_REGION` | `auto` |
| `S3_PATH_STYLE` | `true` |

### 민감 → Secret Manager `--set-secrets` (값은 `.env`의 해당 키에서)
| 시크릿 이름 | 앱 env 변수 | 값 출처(`.env`) |
|---|---|---|
| `readmind-datasource-url` | `SPRING_DATASOURCE_URL` | `SPRING_DATASOURCE_URL` (Neon **다이렉트** JDBC, sslmode=require) |
| `readmind-datasource-username` | `SPRING_DATASOURCE_USERNAME` | `SPRING_DATASOURCE_USERNAME` |
| `readmind-datasource-password` | `SPRING_DATASOURCE_PASSWORD` | `SPRING_DATASOURCE_PASSWORD` |
| `readmind-jwt-secret` | `JWT_SECRET` | `JWT_SECRET` |
| `readmind-r2-access-key` | `S3_KEY` | `S3_KEY` |
| `readmind-r2-secret-key` | `S3_SECRET` | `S3_SECRET` |
| `readmind-ai-service-token` | `AI_SERVICE_TOKEN` | `AI_SERVICE_TOKEN` |

> `POSTGRES_URL`은 **ai-service(psycopg) 전용**. 백엔드는 절대 읽지 않는다 — 헷갈리지 말 것.
> `PORT`는 Cloud Run이 주입한다. **`--set-env-vars`에 넣으면 배포 거부**된다.

## 순서 (반드시 이대로)

### 1) 시크릿 7개 생성 (콘솔 권장, 값 노출 방지)
Secret Manager 콘솔 → CREATE SECRET → 위 표의 이름 + `.env` 값. 7개 반복.
확인:
```bash
gcloud secrets list --project=gen-lang-client-0471683922   # 7개 보여야 함
```

### 2) 런타임 SA에 secretAccessor 부여 (시크릿 생성 후)
```bash
PROJECT=gen-lang-client-0471683922
SA="53543020852-compute@developer.gserviceaccount.com"
for S in readmind-datasource-url readmind-datasource-username readmind-datasource-password \
         readmind-jwt-secret readmind-r2-access-key readmind-r2-secret-key readmind-ai-service-token; do
  gcloud secrets add-iam-policy-binding "$S" --project="$PROJECT" \
    --member="serviceAccount:${SA}" --role="roles/secretmanager.secretAccessor"
done
```

### 3) 배포 (`~/ReadMind`에서)
```bash
cd ~/ReadMind
gcloud run deploy readmind-backend \
  --project=gen-lang-client-0471683922 --region=asia-northeast3 --source=./backend \
  --allow-unauthenticated --port=8080 \
  --memory=1Gi --cpu=1 --cpu-boost --timeout=300 --min-instances=0 --max-instances=3 \
  --set-env-vars=^##^AI_SERVICE_URL=https://dayeongim-readmind-ai.hf.space##S3_ENDPOINT=https://3dca5274a9665e0e934b0a8fc1138d6a.r2.cloudflarestorage.com##S3_BUCKET=readmind-dev##S3_REGION=auto##S3_PATH_STYLE=true \
  --set-secrets=SPRING_DATASOURCE_URL=readmind-datasource-url:latest,SPRING_DATASOURCE_USERNAME=readmind-datasource-username:latest,SPRING_DATASOURCE_PASSWORD=readmind-datasource-password:latest,JWT_SECRET=readmind-jwt-secret:latest,S3_KEY=readmind-r2-access-key:latest,S3_SECRET=readmind-r2-secret-key:latest,AI_SERVICE_TOKEN=readmind-ai-service-token:latest
```
`--set-env-vars`의 `^##^`는 값에 콤마가 섞여도 안전하도록 구분자를 `##`로 바꾸는 gcloud 관례.

### 4) 검증 (Cloud Shell)
```bash
URL=https://readmind-backend-53543020852.asia-northeast3.run.app
curl -i "$URL/api/v1/auth/me"                               # 401 = HTTP/보안필터 정상
curl -i -X POST "$URL/api/v1/auth/signup" -H "Content-Type: application/json" \
  -d '{"email":"cloudrun-test@readmind.dev","password":"test1234"}'   # 200 + 토큰 = Neon+Flyway+JWT 정상
```
context-path가 `/api/v1`이라 모든 경로 접두사는 `/api/v1`.

## 트러블슈팅
- **Creating Revision: "Secret ... not found"** → 1)을 안 했거나 이름 오타. 생성 후 재배포.
- **컨테이너 시작 실패 / 503** → 대개 Flyway가 Neon **풀러** 엔드포인트에 붙음(어드바이저리 락). `readmind-datasource-url`을 **다이렉트** 엔드포인트로.
- **could not find source [./backend]** → 홈(`~`)에서 실행함. `cd ~/ReadMind` 후 재실행.
- **`Regional Access Boundary ... Account not found: ...|llmdayeong@gmail.com`** → 매 호출 반복되나 재시도 후 진행되는 비치명적 노이즈(계정/조직 정책 유령 principal). 배포는 정상 진행됨.
- 재배포는 빌드 캐시로 빠름. 코드 변경 후엔 같은 명령 재실행.
