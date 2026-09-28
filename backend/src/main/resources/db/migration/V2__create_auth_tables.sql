-- user_id 는 users 를 ID 로만 참조한다 (BC 간 FK 를 두지 않는다).
CREATE TABLE credentials (
    credential_id BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    modified_at   TIMESTAMP(6) NOT NULL
);

CREATE TABLE refresh_tokens (
    refresh_token_id BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL UNIQUE,
    expires_at       TIMESTAMP(6) NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    modified_at      TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
