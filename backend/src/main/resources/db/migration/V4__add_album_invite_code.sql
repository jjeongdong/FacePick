-- 앨범당 초대 코드 하나. 앱이 만드는 코드는 base64url 22자다.
ALTER TABLE albums ADD COLUMN invite_code VARCHAR(22);

-- 이미 있는 앨범(개발 DB)은 랜덤 16진수 22자로 채운다. 형식만 다르고 앱에서는 똑같이 쓰인다.
UPDATE albums SET invite_code = left(replace(gen_random_uuid()::text, '-', ''), 22);

ALTER TABLE albums ALTER COLUMN invite_code SET NOT NULL;
ALTER TABLE albums ADD CONSTRAINT uk_albums_invite_code UNIQUE (invite_code);
