-- 지운 사진의 스토리지 파일 삭제 대기열. PhotoStorageCleaner 가 created_at 20분 뒤 원본·썸네일·미리보기를 지운다
-- (업로드 URL 15분 유효 + 처리 중이던 워커가 늦게 올린 파일까지 지우기 위한 지연).
CREATE TABLE photo_storage_deletions (
    deletion_id BIGSERIAL    PRIMARY KEY,
    storage_key VARCHAR(200) NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    deleted_at  TIMESTAMP(6)
);

CREATE INDEX idx_photo_storage_deletions_pending ON photo_storage_deletions (created_at) WHERE deleted_at IS NULL;
