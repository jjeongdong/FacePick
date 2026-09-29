-- face-worker(Python)가 쓰고, 인물 조회 API(person BC)가 읽는다. 백엔드 엔티티는 조회 API 작업 때 매핑한다.
-- photo_id·album_id 는 photos·albums 를 ID 로만 참조한다 (BC 간 FK 를 두지 않는다).
CREATE EXTENSION IF NOT EXISTS vector;

-- 앨범 범위의 인물 클러스터. 앨범 간 사람 매칭은 하지 않는다 (PRD 개인정보 요구사항).
CREATE TABLE persons (
    person_id     BIGSERIAL    PRIMARY KEY,
    album_id      BIGINT       NOT NULL,
    cover_face_id BIGINT,
    created_at    TIMESTAMP(6) NOT NULL,
    modified_at   TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_persons_album ON persons (album_id);

-- bbox 는 미리보기 이미지 기준 픽셀. embedding 은 L2 정규화된 ArcFace 출력이다.
-- 벡터 인덱스(HNSW)를 두지 않는다: 앨범당 얼굴 수만 개라 album_id 로 거른 뒤 정확 검색이 충분히 빠르고,
-- 근사 인덱스는 앨범 필터와 함께 쓰면 결과 수가 모자랄 수 있다.
CREATE TABLE faces (
    face_id    BIGSERIAL    PRIMARY KEY,
    photo_id   BIGINT       NOT NULL,
    album_id   BIGINT       NOT NULL,
    person_id  BIGINT       NOT NULL REFERENCES persons (person_id),
    bbox_x1    INTEGER      NOT NULL,
    bbox_y1    INTEGER      NOT NULL,
    bbox_x2    INTEGER      NOT NULL,
    bbox_y2    INTEGER      NOT NULL,
    det_score  REAL         NOT NULL,
    embedding  vector(512)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_faces_album ON faces (album_id);
CREATE INDEX idx_faces_photo ON faces (photo_id);
CREATE INDEX idx_faces_person ON faces (person_id);

ALTER TABLE persons
    ADD CONSTRAINT fk_persons_cover_face FOREIGN KEY (cover_face_id) REFERENCES faces (face_id);

-- 사진별 분석 완료 표시. 얼굴이 없는 사진도 행이 있어야 중복 수신을 건너뛸 수 있다.
CREATE TABLE face_analyses (
    photo_id    BIGINT       PRIMARY KEY,
    album_id    BIGINT       NOT NULL,
    face_count  INTEGER      NOT NULL,
    analyzed_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_face_analyses_album ON face_analyses (album_id);
