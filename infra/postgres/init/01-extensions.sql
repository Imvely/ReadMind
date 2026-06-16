-- 최초 DB 생성 시 1회 실행 (docker-entrypoint-initdb.d).
-- 스키마(테이블)는 backend Flyway 마이그레이션(V1__init.sql)이 담당한다(명세서 §3, §7).
-- 여기서는 pgvector 확장만 미리 등록해 마이그레이션이 vector(1024) 타입을 쓸 수 있게 한다.
CREATE EXTENSION IF NOT EXISTS vector;
