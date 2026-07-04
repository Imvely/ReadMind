-- §4.5 동기화 준비 (명세서 2026-07-05 갱신 반영).
-- 1) bookmarks에 커서 필터용 updated_at 누락 → 추가. 기존 행은 created_at으로 초기화.
ALTER TABLE bookmarks
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
UPDATE bookmarks SET updated_at = created_at;

-- 2) updated_at 자동 갱신 트리거 — JPA 매핑이 read-only(DB 소유)이므로 UPDATE 시
--    DB가 직접 갱신해야 sync 커서(updated_at > since)가 수정분을 놓치지 않는다.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_highlights_updated_at BEFORE UPDATE ON highlights
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_notes_updated_at BEFORE UPDATE ON notes
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_bookmarks_updated_at BEFORE UPDATE ON bookmarks
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_reading_progress_updated_at BEFORE UPDATE ON reading_progress
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
