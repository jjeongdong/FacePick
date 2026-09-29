import secrets
from collections.abc import Iterator

import numpy as np
import psycopg
import pytest

from face_worker.config import Config, load_config
from face_worker.face import DetectedFace

DIMENSIONS = 512


def unit(*weights: float) -> np.ndarray:
    """앞 칸들에 weights 를 넣고 L2 정규화한 512차원 벡터. unit(1) 과 unit(0, 1) 은 유사도 0."""
    vector = np.zeros(DIMENSIONS, dtype=np.float32)
    vector[: len(weights)] = weights
    return vector / np.linalg.norm(vector)


@pytest.fixture
def unit_face():
    def make(*weights: float, det_score: float = 0.9, size: int = 100) -> DetectedFace:
        return DetectedFace(
            bbox=(10, 10, 10 + size, 10 + size), det_score=det_score, embedding=unit(*weights)
        )

    return make


@pytest.fixture(scope="session")
def config() -> Config:
    return load_config()


@pytest.fixture
def unique_album_id() -> int:
    # 개발 DB 의 실제 앨범과 겹치지 않는 큰 값. photos·faces 는 albums 에 FK 가 없다.
    return 900_000_000 + secrets.randbelow(100_000_000)


@pytest.fixture
def insert_photo(config: Config) -> Iterator:
    inserted: list[int] = []

    def insert(album_id: int) -> int:
        content_hash = secrets.token_hex(32)
        with psycopg.connect(config.database_url) as conn:
            photo_id = conn.execute(
                """
                INSERT INTO photos (album_id, uploader_id, content_hash, byte_size, content_type,
                                    storage_key, status, uploaded_at, created_at, modified_at)
                VALUES (%s, 1, %s, 1, 'image/jpeg', %s, 'UPLOADED', now(), now(), now())
                RETURNING photo_id
                """,
                (album_id, content_hash, f"albums/{album_id}/originals/{content_hash}"),
            ).fetchone()[0]
        inserted.append(photo_id)
        return photo_id

    yield insert
    with psycopg.connect(config.database_url) as conn:
        conn.execute("DELETE FROM photos WHERE photo_id = ANY(%s)", (inserted,))


@pytest.fixture
def cleanup_faces(config: Config, unique_album_id: int) -> Iterator[None]:
    yield
    with psycopg.connect(config.database_url) as conn:
        # persons.cover_face_id 와 faces.person_id 가 서로를 가리키므로 대표 얼굴부터 끊는다.
        conn.execute(
            "UPDATE persons SET cover_face_id = NULL WHERE album_id = %s", (unique_album_id,)
        )
        conn.execute("DELETE FROM faces WHERE album_id = %s", (unique_album_id,))
        conn.execute("DELETE FROM persons WHERE album_id = %s", (unique_album_id,))
        conn.execute("DELETE FROM face_analyses WHERE album_id = %s", (unique_album_id,))
