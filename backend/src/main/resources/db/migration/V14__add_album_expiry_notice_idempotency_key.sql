-- Resend Idempotency-Key 를 notice_id 대신 무작위 값으로 만든다.
-- Resend 는 키를 계정 범위로 24시간 기억해서, 개발 DB 를 초기화하거나 여러 환경이 한 계정을 쓰면
-- 같은 notice_id 가 다른 메일의 키가 된다 (다른 내용이면 409 로 영구 실패, 같으면 보내지 않고 성공 처리).
ALTER TABLE album_expiry_notices ADD COLUMN idempotency_key UUID NOT NULL DEFAULT gen_random_uuid();
