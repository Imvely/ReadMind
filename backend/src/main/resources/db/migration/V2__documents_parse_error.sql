-- 파싱 FAILED 사유 저장 (명세서 §3 documents.parse_error).
-- 사유 없는 실패 금지: 실패 시 예외 요약을 남기고, 성공/재시도 시 NULL로 초기화한다.
ALTER TABLE documents ADD COLUMN parse_error TEXT;
