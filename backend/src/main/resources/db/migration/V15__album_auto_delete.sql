-- 만료 앨범 자동 삭제.
-- album_storage_deletions: DB 정리를 마친 앨범의 스토리지 prefix(albums/{id}/) 삭제 대기열. AlbumStorageCleaner 가 created_at 20분 뒤 비운다
-- (만료 직전 받은 업로드 URL(15분)과 처리 중이던 워커가 늦게 올린 파일까지 지우기 위한 지연). 앨범당 한 행.
-- album_id 는 albums 를 ID 로만 참조한다 (앨범 행이 먼저 사라지므로 FK 를 두지 않는다).
CREATE TABLE album_storage_deletions (
    album_id   BIGINT       PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6)
);

CREATE INDEX idx_album_storage_deletions_pending ON album_storage_deletions (created_at) WHERE deleted_at IS NULL;

-- 만료 앨범 조회용 (expires_at <= now 오래된 순).
CREATE INDEX idx_albums_expires ON albums (expires_at, album_id);

-- 조각 삭제용. 기존 photos 인덱스는 purpose·status 부분 인덱스라 PENDING·SELFIE 까지 앨범 단위로 찾지 못한다.
CREATE INDEX idx_photos_album ON photos (album_id, photo_id);
