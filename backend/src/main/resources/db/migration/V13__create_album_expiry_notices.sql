-- 앨범 만료 7일 전 알림 메일 대기열. 멤버마다 한 행이라 스캐너가 여러 번 돌아도 한 번만 보낸다.
-- next_attempt_at 은 재시도 시각이자 선점 임대 만료 시각이다 (발송 중 서버가 죽으면 임대가 끝난 뒤 다시 잡힌다).
CREATE TABLE album_expiry_notices (
    notice_id           BIGSERIAL    PRIMARY KEY,
    album_id            BIGINT       NOT NULL REFERENCES albums (album_id) ON DELETE CASCADE,
    user_id             BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    attempts            INT          NOT NULL,
    next_attempt_at     TIMESTAMP(6) NOT NULL,
    provider_message_id VARCHAR(100),
    last_error          VARCHAR(500),
    sent_at             TIMESTAMP(6),
    created_at          TIMESTAMP(6) NOT NULL,
    modified_at         TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_album_expiry_notices_album_user UNIQUE (album_id, user_id)
);

CREATE INDEX idx_album_expiry_notices_due ON album_expiry_notices (next_attempt_at) WHERE status = 'PENDING';
