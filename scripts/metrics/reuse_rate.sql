-- ReadMind Phase 0 핵심 지표: 재사용률 (명세서 §7, CLAUDE.md §7)
-- "같은 사용자가 다른 논문을 또 올리는가" — 기능 완성보다 이 지표가 우선.
-- 스키마 변경 없이 documents(user_id, created_at, deleted_at)만으로 산출.
--
-- 실행(컨테이너 postgres 기준):
--   docker exec -i readmind-postgres psql -U readmind -d readmind -f - < scripts/metrics/reuse_rate.sql
-- 또는 psql 접속 후 \i scripts/metrics/reuse_rate.sql

-- ── 1) 재사용률: 서로 다른 날 2건 이상 올린 유저 / 1건 이상 올린 유저 ──
WITH per_user AS (
    SELECT user_id,
           COUNT(*)                          AS docs,
           COUNT(DISTINCT date(created_at))  AS active_days,
           MIN(created_at)                   AS first_upload,
           MAX(created_at)                   AS last_upload
    FROM documents
    WHERE deleted_at IS NULL
    GROUP BY user_id
)
SELECT
    COUNT(*) FILTER (WHERE docs >= 1)                                        AS uploaders,
    COUNT(*) FILTER (WHERE docs >= 2)                                        AS multi_doc_users,
    COUNT(*) FILTER (WHERE docs >= 2 AND active_days >= 2)                   AS repeat_users,
    ROUND(100.0 * COUNT(*) FILTER (WHERE docs >= 2 AND active_days >= 2)
          / NULLIF(COUNT(*) FILTER (WHERE docs >= 1), 0), 1)                AS reuse_rate_pct
FROM per_user;

-- ── 2) 보조: 업로드 수 분포(1건 / 2~3건 / 4건+) ──
WITH per_user AS (
    SELECT user_id, COUNT(*) AS docs
    FROM documents WHERE deleted_at IS NULL
    GROUP BY user_id
)
SELECT
    CASE WHEN docs = 1 THEN '1'
         WHEN docs BETWEEN 2 AND 3 THEN '2-3'
         ELSE '4+' END           AS docs_bucket,
    COUNT(*)                      AS users
FROM per_user
GROUP BY 1
ORDER BY 1;

-- ── 3) 보조: 활성화율(업로드 후 요약을 1건이라도 생성한 유저 비율) ──
--     AI 가치 경험 여부의 프록시. summaries.document_id → documents.user_id.
SELECT
    COUNT(DISTINCT d.user_id)                                                AS uploaders,
    COUNT(DISTINCT d.user_id) FILTER (WHERE s.document_id IS NOT NULL)       AS activated_users,
    ROUND(100.0 * COUNT(DISTINCT d.user_id) FILTER (WHERE s.document_id IS NOT NULL)
          / NULLIF(COUNT(DISTINCT d.user_id), 0), 1)                         AS activation_pct
FROM documents d
LEFT JOIN summaries s ON s.document_id = d.id
WHERE d.deleted_at IS NULL;
