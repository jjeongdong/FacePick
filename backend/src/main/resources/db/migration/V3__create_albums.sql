-- owner_id, user_id 는 users 를 ID 로만 참조한다 (BC 간 FK 를 두지 않는다).
CREATE TABLE albums (
    album_id    BIGSERIAL    PRIMARY KEY,
    owner_id    BIGINT       NOT NULL,
    title       VARCHAR(50)  NOT NULL,
    expires_at  TIMESTAMP(6) NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    modified_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE album_members (
    album_member_id BIGSERIAL    PRIMARY KEY,
    album_id        BIGINT       NOT NULL REFERENCES albums (album_id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    modified_at     TIMESTAMP(6) NOT NULL,
    UNIQUE (album_id, user_id)
);

CREATE INDEX idx_album_members_user ON album_members (user_id, created_at DESC);
