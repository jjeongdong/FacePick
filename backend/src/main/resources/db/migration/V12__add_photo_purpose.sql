-- 셀피(내 사진 등록)도 photos 에 두어 썸네일·얼굴 분석 파이프라인을 그대로 탄다. 앨범 사진 API 는 ALBUM 만 본다.
ALTER TABLE photos ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'ALBUM';

-- "같은 파일 한 번만"은 앨범 사진끼리만. 이미 올린 앨범 사진을 셀피로 다시 고를 수 있어야 한다.
ALTER TABLE photos DROP CONSTRAINT uk_photos_album_hash;
CREATE UNIQUE INDEX uk_photos_album_hash ON photos (album_id, content_hash) WHERE purpose = 'ALBUM';

-- 셀피는 멤버당 앨범에 하나라 "나"가 하나로 정해진다.
CREATE UNIQUE INDEX uk_photos_selfie_uploader ON photos (album_id, uploader_id) WHERE purpose = 'SELFIE';
