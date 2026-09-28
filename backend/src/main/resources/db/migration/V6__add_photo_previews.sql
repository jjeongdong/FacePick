-- 썸네일 워커(Python)가 채운다. 백엔드 Photo 엔티티는 사진 목록 API 작업 때 매핑한다
-- (ddl-auto: validate 는 엔티티에 없는 컬럼을 검사하지 않는다).
-- status 에 새 값을 쓰면 백엔드 PhotoStatus enum 이 읽지 못하므로, 처리 완료는 processed_at 으로 판단한다.
ALTER TABLE photos
    ADD COLUMN thumbnail_key VARCHAR(200),
    ADD COLUMN preview_key   VARCHAR(200),
    ADD COLUMN width         INTEGER,
    ADD COLUMN height        INTEGER,
    ADD COLUMN taken_at      TIMESTAMP(6),
    ADD COLUMN processed_at  TIMESTAMP(6);
