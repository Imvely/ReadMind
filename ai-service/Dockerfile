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

# 애플리케이션 소스.
COPY app ./app

# 루트가 아닌 사용자로 실행(권한 최소화).
RUN useradd --system --uid 1001 appuser
USER appuser

EXPOSE 8000

# 명세서 §5 진입점: app/main.py 의 FastAPI app.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
