-- ReadMind 초기 스키마 (명세서 §3). 백엔드 Flyway가 스키마의 SSOT.
-- AI 서비스(M1)는 이 테이블에 적재/조회만 한다(DDL 생성 안 함).

CREATE EXTENSION IF NOT EXISTS vector;

-- ============ 사용자/구독 ============
CREATE TABLE users (
  id              BIGSERIAL PRIMARY KEY,
  email           VARCHAR(255) UNIQUE NOT NULL,
  password_hash   VARCHAR(255),
  display_name    VARCHAR(100),
  tier            VARCHAR(20) NOT NULL DEFAULT 'FREE',
  school_verified BOOLEAN NOT NULL DEFAULT false,
  locale          VARCHAR(10) NOT NULL DEFAULT 'ko',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  plan          VARCHAR(20) NOT NULL,
  status        VARCHAR(20) NOT NULL,
  provider      VARCHAR(20) NOT NULL,
  provider_ref  VARCHAR(255),
  current_period_end TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_quotas (
  user_id       BIGINT PRIMARY KEY REFERENCES users(id),
  period_start  DATE NOT NULL,
  ai_summary_used   INT NOT NULL DEFAULT 0,
  ai_qa_used        INT NOT NULL DEFAULT 0,
  ai_translate_used INT NOT NULL DEFAULT 0,
  storage_bytes     BIGINT NOT NULL DEFAULT 0,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ 문서 ============
CREATE TABLE documents (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  title         VARCHAR(500) NOT NULL,
  format        VARCHAR(20) NOT NULL,
  storage_key   VARCHAR(500) NOT NULL,
  file_size     BIGINT NOT NULL,
  page_count    INT,
  language      VARCHAR(10),
  parse_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  is_synced     BOOLEAN NOT NULL DEFAULT false,
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_user ON documents(user_id) WHERE deleted_at IS NULL;

CREATE TABLE document_chunks (
  id            BIGSERIAL PRIMARY KEY,
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  chunk_index   INT NOT NULL,
  page_no       INT,
  content       TEXT NOT NULL,
  embedding     vector(1024),
  token_count   INT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(document_id, chunk_index)
);
CREATE INDEX idx_chunks_doc ON document_chunks(document_id);

-- ============ 주석류(동기화 대상) ============
CREATE TABLE highlights (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  page_no       INT,
  location      JSONB NOT NULL,
  selected_text TEXT NOT NULL,
  color         VARCHAR(20) NOT NULL DEFAULT 'yellow',
  note          TEXT,
  ai_suggested  BOOLEAN NOT NULL DEFAULT false,
  tags          TEXT[],
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_highlights_user_doc ON highlights(user_id, document_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_highlights_search ON highlights USING gin (to_tsvector('simple', selected_text));

CREATE TABLE notes (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  page_no       INT,
  body          TEXT NOT NULL,
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bookmarks (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  location      JSONB NOT NULL,
  label         VARCHAR(200),
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reading_progress (
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  location      JSONB NOT NULL,
  percent       NUMERIC(5,2) NOT NULL DEFAULT 0,
  client_updated_at TIMESTAMPTZ,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, document_id)
);

-- ============ AI 산출물 ============
CREATE TABLE summaries (
  id            BIGSERIAL PRIMARY KEY,
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  scope         VARCHAR(20) NOT NULL,
  scope_ref     JSONB,
  style         VARCHAR(20) NOT NULL DEFAULT 'PAPER',
  content       JSONB NOT NULL,
  model         VARCHAR(50),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(document_id, scope, scope_ref, style)
);

CREATE TABLE qa_sessions (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE qa_messages (
  id            BIGSERIAL PRIMARY KEY,
  session_id    BIGINT NOT NULL REFERENCES qa_sessions(id) ON DELETE CASCADE,
  role          VARCHAR(10) NOT NULL,
  content       TEXT NOT NULL,
  sources       JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE flashcards (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL REFERENCES users(id),
  document_id   BIGINT REFERENCES documents(id) ON DELETE SET NULL,
  source_highlight_id BIGINT REFERENCES highlights(id) ON DELETE SET NULL,
  front         TEXT NOT NULL,
  back          TEXT NOT NULL,
  fsrs_stability   NUMERIC,
  fsrs_difficulty  NUMERIC,
  due_at        TIMESTAMPTZ,
  last_review_at TIMESTAMPTZ,
  state         VARCHAR(20) NOT NULL DEFAULT 'NEW',
  client_updated_at TIMESTAMPTZ,
  version       INT NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_flashcards_due ON flashcards(user_id, due_at) WHERE deleted_at IS NULL;
