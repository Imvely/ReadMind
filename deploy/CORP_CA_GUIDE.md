# 사내 CA 주입 + 라이브 e2e 실행 가이드 (직접 따라 하기)

사내 프록시가 HTTPS를 가로채는(MITM) 환경이라, 컨테이너(ai-service)가 외부 HTTPS
(Gemini API·Cloudflare R2)에 붙을 때 인증서 검증에 실패한다. 아래대로 사내 루트 CA를
컨테이너에 신뢰시키면 실제 업로드→파싱→요약→Q&A(e2e)가 돌아간다.

> 실행은 PowerShell(레포 루트 `C:\dayeong\99.etc\ReadMind`)에서. 코드는 이미 준비돼 있다
> (ai-service/Dockerfile 이 `ai-service/certs/*.crt` 를 자동으로 신뢰 번들에 추가).

---

## STEP 1 — 사내 루트 CA 를 `ai-service/certs/` 에 저장

### 방법 A) IT가 준 CA 파일이 있으면
그 `.crt`(PEM/Base64) 파일을 `ai-service\certs\corp-root.crt` 로 복사하고 STEP 2로.

### 방법 B) 프록시가 제시하는 체인에서 직접 추출 (PowerShell)
아래를 통째로 붙여넣어 실행한다. 현재 연결이 가로채이는 체인의 CA(중간+루트)를 뽑아 PEM으로 저장한다.

```powershell
$h = 'www.google.com'
$tcp = New-Object System.Net.Sockets.TcpClient($h, 443)
$ssl = New-Object System.Net.Security.SslStream($tcp.GetStream(), $false, ([System.Net.Security.RemoteCertificateValidationCallback]{ $true }))
$ssl.AuthenticateAsClient($h)
$leaf = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($ssl.RemoteCertificate)
$ssl.Dispose(); $tcp.Close()
$chain = New-Object System.Security.Cryptography.X509Certificates.X509Chain
$chain.ChainPolicy.RevocationMode = 'NoCheck'
[void]$chain.Build($leaf)
$pem = ''
# 리프(0)를 뺀 CA(중간+루트)만 저장. 하나도 없으면 리프라도 저장.
$startIdx = if ($chain.ChainElements.Count -gt 1) { 1 } else { 0 }
for ($i = $startIdx; $i -lt $chain.ChainElements.Count; $i++) {
  $c = $chain.ChainElements[$i].Certificate
  $b64 = [Convert]::ToBase64String($c.RawData, 'InsertLineBreaks')
  $pem += "# $($c.Subject)`r`n-----BEGIN CERTIFICATE-----`r`n$b64`r`n-----END CERTIFICATE-----`r`n"
  Write-Host ("CA[{0}]: {1}" -f $i, $c.Subject)
}
New-Item -ItemType Directory -Force ai-service\certs | Out-Null
Set-Content -Path ai-service\certs\corp-root.crt -Value $pem -Encoding ascii
Write-Host "→ saved ai-service\certs\corp-root.crt"
```

**확인**: 출력된 `CA[..]: ...` subject 에 회사/프록시/보안솔루션 이름이 보이면 그게 사내 CA다.
파일 첫 줄이 `# ...` / `-----BEGIN CERTIFICATE-----` 로 시작하는지 확인
(만약 깨진 바이너리면 Fasoo DRM 이 건드린 것 — 그때 알려줘).

---

## STEP 2 — 호스트 8000 포트 충돌 회피 (override)

지금 이 PC엔 다른 프로젝트 컨테이너(`ocr-ingest-api`)가 호스트 8000을 쓰고 있어
ai-service 퍼블리시가 충돌한다. 레포 루트에 아래 파일을 만든다(자동 로드, gitignore됨).

`docker-compose.override.yml`:
```yaml
# 로컬 전용. ai-service 호스트 8000 퍼블리시 제거(backend↔ai 는 내부망이라 무관).
services:
  ai-service:
    ports: !override []
```

> 다른 PC(충돌 없음)에서는 이 파일 없이도 된다.

---

## STEP 3 — `.env` 확인 (Gemini 임베딩 키 1개만 추가)

`.env` 에 이미 넣은 값 외에, **임베딩 모델**이 있는지 확인(없으면 추가):
```
LLM_PROVIDER=gemini
LLM_MODEL=gemini-2.5-flash
LLM_API_KEY=...(발급키)
EMBEDDING_MODEL=gemini-embedding-001
EMBEDDING_DIM=1024
```
- 임베딩은 `LLM_API_KEY` 를 재사용하므로 `EMBEDDING_API_KEY` 는 안 넣어도 된다.
- `S3_*`(R2) 는 이미 넣은 값 그대로.

---

## STEP 4 — ai-service 재빌드 & 기동

```powershell
docker compose --profile app build ai-service
docker compose --profile app up -d --force-recreate ai-service backend
```
(`docker-compose.override.yml` 은 자동 적용된다.)

---

## STEP 5 — CA 가 실제로 먹혔는지 확인 (외부 HTTPS 200)

```powershell
docker exec readmind-ai-service python -c "import httpx; print('google', httpx.get('https://www.google.com', timeout=10).status_code)"
docker exec readmind-ai-service python -c "import boto3,os; print('r2 endpoint reachable via botocore CA:', bool(os.environ.get('AWS_CA_BUNDLE')))"
```
- 첫 줄이 `google 200` 이면 성공(= Gemini/R2 TLS 통과). `CERTIFICATE_VERIFY_FAILED` 가 뜨면
  STEP 1의 CA가 틀린 것 — subject 다시 확인하거나 IT CA 파일로 교체.

---

## STEP 6 — 라이브 e2e (브라우저)

1. 브라우저에서 **https://localhost** 접속(Caddy). 로컬 인증서 경고는 "고급 → 계속"으로 진행.
   - 또는 dev 서버: `npm --prefix web run dev` 후 http://localhost:5173 (백엔드 프록시 8080).
2. 회원가입/로그인(아무 이메일 + 8자+ 비번).
3. **PDF 업로드** → 카드가 `분석 중…` → `준비됨(READY)` 으로 바뀔 때까지 대기(파싱=R2 다운로드+Gemini 임베딩).
   - READY 안 되고 `분석 실패` 면: `docker logs readmind-ai-service` 로 원인 확인
     (대개 CA(STEP5) 또는 R2 CORS(아래) 문제).
4. **요약 생성** → Gemini 로 PAPER 요약.
5. **Q&A** 탭 → 질문 → 답변 + 근거 칩 클릭 시 본문 점프.

### R2 CORS (브라우저 업로드용) — 한 번만
브라우저가 presigned PUT/GET 으로 R2 에 직접 붙으므로 버킷에 CORS 정책이 필요하다.
Cloudflare 대시보드 → R2 → `readmind-dev` → Settings → CORS Policy:
```json
[{ "AllowedOrigins": ["https://localhost", "http://localhost:5173"],
   "AllowedMethods": ["PUT", "GET"],
   "AllowedHeaders": ["*"],
   "ExposeHeaders": ["ETag"] }]
```
(실도메인 배포 시 그 도메인을 AllowedOrigins 에 추가.)

---

## 문제 해결
- `docker logs readmind-ai-service` / `docker logs readmind-backend` 로 스택 로그 확인.
- 업로드는 되는데 파싱 실패 → 십중팔구 STEP 5(CA) 미해결 또는 R2 자격/버킷명 불일치.
- 브라우저 업로드 자체가 CORS 에러 → R2 CORS 정책(위) 누락.
- 요약/QA 만 실패 → Gemini 키/모델명 또는 CA. `LLM_MODEL=gemini-2.5-flash` 확인.
```
