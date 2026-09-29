-- 사진을 지우면 백엔드가 그 사진의 얼굴을 지운다. 대표 얼굴이 지워지면 cover_face_id 를 자동으로 비워,
-- 정리 단계에서 남은 얼굴로 다시 고르거나(얼굴이 없으면) 인물을 지운다.
ALTER TABLE persons DROP CONSTRAINT fk_persons_cover_face;
ALTER TABLE persons
    ADD CONSTRAINT fk_persons_cover_face FOREIGN KEY (cover_face_id) REFERENCES faces (face_id) ON DELETE SET NULL;

-- 얼굴을 지울 때마다 하는 FK 검사가 persons 를 전체 스캔하지 않게 한다.
CREATE INDEX idx_persons_cover_face ON persons (cover_face_id);
