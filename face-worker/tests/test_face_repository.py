import psycopg
import pytest

from face_worker.face_repository import FaceRepository

pytestmark = pytest.mark.integration


@pytest.fixture
def repository(config) -> FaceRepository:
    return FaceRepository(config.database_url)


def count(config, sql: str, album_id: int) -> int:
    with psycopg.connect(config.database_url) as conn:
        return conn.execute(sql, (album_id,)).fetchone()[0]


def test_saves_person_face_cover_and_analysis_together(
    repository, config, unique_album_id, unit_face, cleanup_faces
):
    # face_analyses.photo_id 는 PK 라 실제 사진 번호와 겹치지 않게 앨범 ID 와 같은 큰 값을 쓴다.
    photo_id = unique_album_id
    face = unit_face(1, 0, det_score=0.8)

    with repository.album_transaction(unique_album_id) as album:
        person_id = album.create_person()
        face_id = album.insert_face(photo_id, person_id, face)
        album.update_cover_if_better(person_id, face_id, face.det_score)
        album.mark_analyzed(photo_id, 1)

    assert repository.is_analyzed(photo_id)
    with psycopg.connect(config.database_url) as conn:
        row = conn.execute(
            """
            SELECT p.cover_face_id, f.photo_id, f.album_id, f.bbox_x1, f.bbox_x2, f.det_score
            FROM persons p JOIN faces f ON f.person_id = p.person_id
            WHERE p.person_id = %s
            """,
            (person_id,),
        ).fetchone()
    assert row[0] == face_id
    assert row[1:5] == (photo_id, unique_album_id, 10, 110)
    assert row[5] == pytest.approx(0.8)


def test_nearest_faces_are_sorted_and_limited_to_album(
    repository, config, unique_album_id, unit_face, cleanup_faces
):
    other_album_id = unique_album_id + 1
    with repository.album_transaction(unique_album_id) as album:
        near_person = album.create_person()
        far_person = album.create_person()
        album.insert_face(1, near_person, unit_face(1, 0.1))
        album.insert_face(2, far_person, unit_face(0, 1))
    try:
        with repository.album_transaction(other_album_id) as other:
            # 다른 앨범에 질의와 똑같은 얼굴을 넣어도 결과에 섞이면 안 된다 (앨범 간 매칭 금지).
            other.insert_face(3, other.create_person(), unit_face(1, 0))

        with repository.album_transaction(unique_album_id) as album:
            neighbors = album.nearest_faces(unit_face(1, 0).embedding, 5)
    finally:
        with psycopg.connect(config.database_url) as conn:
            conn.execute("DELETE FROM faces WHERE album_id = %s", (other_album_id,))
            conn.execute("DELETE FROM persons WHERE album_id = %s", (other_album_id,))

    assert [n.person_id for n in neighbors] == [near_person, far_person]
    assert neighbors[0].similarity == pytest.approx(0.995, abs=1e-3)
    assert neighbors[1].similarity == pytest.approx(0.0, abs=1e-3)


def test_nearest_faces_respects_k(repository, unique_album_id, unit_face, cleanup_faces):
    with repository.album_transaction(unique_album_id) as album:
        person_id = album.create_person()
        for photo_id in range(1, 8):
            album.insert_face(photo_id, person_id, unit_face(1, photo_id / 100))

        assert len(album.nearest_faces(unit_face(1, 0).embedding, 5)) == 5


def test_cover_changes_only_for_better_face(
    repository, config, unique_album_id, unit_face, cleanup_faces
):
    with repository.album_transaction(unique_album_id) as album:
        person_id = album.create_person()
        first = album.insert_face(1, person_id, unit_face(1, 0, det_score=0.8))
        album.update_cover_if_better(person_id, first, 0.8)
        worse = album.insert_face(2, person_id, unit_face(1, 0.1, det_score=0.7))
        album.update_cover_if_better(person_id, worse, 0.7)
        better = album.insert_face(3, person_id, unit_face(1, 0.2, det_score=0.95))
        album.update_cover_if_better(person_id, better, 0.95)

    with psycopg.connect(config.database_url) as conn:
        cover = conn.execute(
            "SELECT cover_face_id FROM persons WHERE person_id = %s", (person_id,)
        ).fetchone()[0]
    assert cover == better


def test_error_rolls_back_everything(repository, config, unique_album_id, unit_face, cleanup_faces):
    photo_id = unique_album_id
    with pytest.raises(RuntimeError), repository.album_transaction(unique_album_id) as album:
        album.insert_face(photo_id, album.create_person(), unit_face(1, 0))
        album.mark_analyzed(photo_id, 1)
        raise RuntimeError("저장 도중 끊김")

    assert not repository.is_analyzed(photo_id)
    assert count(config, "SELECT count(*) FROM persons WHERE album_id = %s", unique_album_id) == 0
    assert count(config, "SELECT count(*) FROM faces WHERE album_id = %s", unique_album_id) == 0


def test_analysis_is_visible_inside_transaction(repository, unique_album_id, cleanup_faces):
    photo_id = unique_album_id
    with repository.album_transaction(unique_album_id) as album:
        assert not album.is_analyzed(photo_id)
        album.mark_analyzed(photo_id, 0)
        assert album.is_analyzed(photo_id)


def test_photo_exists(repository, insert_photo, unique_album_id):
    photo_id = insert_photo(unique_album_id)

    assert repository.photo_exists(photo_id)
    assert not repository.photo_exists(photo_id + 1_000_000_000)


def test_photo_exists_inside_transaction(repository, insert_photo, unique_album_id):
    photo_id = insert_photo(unique_album_id)

    with repository.album_transaction(unique_album_id) as album:
        assert album.photo_exists(photo_id)
        assert not album.photo_exists(photo_id + 1_000_000_000)
