-- album_id, uploader_id 는 albums·users 를 ID 로만 참조한다 (BC 간 FK 를 두지 않는다).
-- 문자열 컬럼은 VARCHAR 로 둔다. CHAR·TEXT 는 ddl-auto: validate 가 String 과 타입 불일치로 본다.
CREATE TABLE photos (
    photo_id     BIGSERIAL    PRIMARY KEY,
    album_id     BIGINT       NOT NULL,
    uploader_id  BIGINT       NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    byte_size    BIGINT       NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    storage_key  VARCHAR(200) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    uploaded_at  TIMESTAMP(6),
    created_at   TIMESTAMP(6) NOT NULL,
    modified_at  TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_photos_album_hash UNIQUE (album_id, content_hash)
);

-- 완료 처리와 같은 트랜잭션에 기록해 두고 PhotoOutboxRelay 가 발행한다 (커밋된 사진만 워커에 전달).
CREATE TABLE photo_outbox (
    outbox_id    BIGSERIAL     PRIMARY KEY,
    topic        VARCHAR(100)  NOT NULL,
    message_key  VARCHAR(100)  NOT NULL,
    payload      VARCHAR(2000) NOT NULL,
    created_at   TIMESTAMP(6)  NOT NULL,
    published_at TIMESTAMP(6)
);

CREATE INDEX idx_photo_outbox_unpublished ON photo_outbox (outbox_id) WHERE published_at IS NULL;
