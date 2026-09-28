-- 사진 목록(GET /api/albums/{albumId}/photos)의 커서 페이징용. 정렬과 같은 순서라 몇 번째 페이지든 인덱스만 따라간다.
CREATE INDEX idx_photos_album_uploaded
    ON photos (album_id, uploaded_at DESC, photo_id DESC)
    WHERE status = 'UPLOADED';
