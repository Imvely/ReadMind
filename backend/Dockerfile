# ReadMind 백엔드 (M2, Spring Boot/Kotlin). 로컬 compose + Google Cloud Run 공용.
# 멀티스테이지: gradle 로 bootJar 빌드 → 슬림 JRE 런타임.
# 포트는 application.yml 의 server.port=${PORT:8080} 로 바인딩 — Cloud Run 이 주입하는 PORT 를 존중.

# ── 1) 빌드 스테이지 ──
FROM gradle:8.11.1-jdk21 AS build
WORKDIR /src

# 의존성 캐시 레이어: 빌드 스크립트만 먼저 복사해 의존성 해석을 캐시한다.
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon >/dev/null 2>&1 || true

# 소스 복사 후 실행 가능한 bootJar 생성(테스트는 CI에서 별도 — 이미지 빌드는 아티팩트만).
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ── 2) 런타임 스테이지 ──
FROM eclipse-temurin:21-jre
WORKDIR /app

# 루트가 아닌 사용자로 실행(권한 최소화).
RUN useradd --system --uid 1001 appuser

# bootJar 산출물만 복사(readmind-backend-<version>.jar).
COPY --from=build /src/build/libs/readmind-backend-*.jar app.jar

USER appuser
EXPOSE 8080

# Flyway 마이그레이션 + API. DB/Redis/S3/AI 접속정보는 env_file(.env)로 주입(§3 시크릿 비하드코딩).
ENTRYPOINT ["java", "-jar", "app.jar"]
