# ReadMind AI 서비스 (M1, FastAPI). 내부 전용(명세서 §5).
# docker compose --profile app 로 기동. 포트 8000.
FROM python:3.12-slim

# PyMuPDF/psycopg[binary]/boto3는 manylinux 휠로 시스템 라이브러리 추가 설치가 필요 없다.
# 파이썬 런타임 위생: 바이트코드 미기록 + 로그 즉시 플러시.
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# 의존성 레이어를 소스와 분리해 캐시 활용(요구사항 안 바뀌면 재설치 안 함).
COPY requirements.txt ./
RUN pip install -r requirements.txt

# ── 사내 프록시(HTTPS MITM) 대응: 루트 CA 신뢰 → 외부 HTTPS(Gemini/R2) 호출 가능 ──
# certs/ 에 사내 루트 CA(.crt, PEM/Base64)를 두고 재빌드하면 자동 반영. 없으면 no-op.
# google-genai(httpx)는 certifi 번들 파일을 직접 읽으므로 그 파일에 append,
# boto3(botocore)/requests 는 CA 번들 env 로 지정(각자 CA 소스가 달라 전부 커버).
COPY certs/ /tmp/corp-certs/
RUN CERTIFI="$(python -c 'import certifi; print(certifi.where())')" && \
    if ls /tmp/corp-certs/*.crt >/dev/null 2>&1; then \
        cat /tmp/corp-certs/*.crt >> "$CERTIFI" && \
        echo "corp CA appended → $CERTIFI"; \
    else \
        echo "no corp CA in certs/ — skip (외부 HTTPS가 사내망에서 막힐 수 있음)"; \
    fi && \
    ln -sf "$CERTIFI" /etc/ssl/certifi-bundle.pem
ENV REQUESTS_CA_BUNDLE=/etc/ssl/certifi-bundle.pem \
    AWS_CA_BUNDLE=/etc/ssl/certifi-bundle.pem \
    SSL_CERT_FILE=/etc/ssl/certifi-bundle.pem

# 애플리케이션 소스.
COPY app ./app

# 루트가 아닌 사용자로 실행(권한 최소화).
RUN useradd --system --uid 1001 appuser
USER appuser

EXPOSE 8000

# 명세서 §5 진입점: app/main.py 의 FastAPI app.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
