-- 가입 전 이메일 인증 코드. 이메일당 한 행이며 새 코드를 보내면 덮어쓴다 (이전 코드 무효).
-- 코드는 SHA-256 해시로만 저장한다.
CREATE TABLE email_verifications (
    email_verification_id BIGSERIAL    PRIMARY KEY,
    email                 VARCHAR(254) NOT NULL UNIQUE,
    code_hash             VARCHAR(64)  NOT NULL,
    expires_at            TIMESTAMP(6) NOT NULL,
    attempts              INT          NOT NULL,
    last_sent_at          TIMESTAMP(6) NOT NULL,
    created_at            TIMESTAMP(6) NOT NULL,
    modified_at           TIMESTAMP(6) NOT NULL
);
