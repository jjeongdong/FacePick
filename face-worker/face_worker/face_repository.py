from collections.abc import Iterator
from contextlib import contextmanager
from datetime import datetime

import numpy as np
import psycopg
from pgvector.psycopg import register_vector

from face_worker.assigner import Neighbor
from face_worker.face import DetectedFace


class FaceRepository:
    def __init__(self, database_url: str):
        self._database_url = database_url

    # 호출마다 연결한다. 연결 하나를 들고 있으면 DB 재시작 뒤 재시도가 끊긴 연결로 영원히 실패한다.
    def is_analyzed(self, photo_id: int) -> bool:
        with psycopg.connect(self._database_url) as conn:
            return _is_analyzed(conn, photo_id)

    def photo_exists(self, photo_id: int) -> bool:
        with psycopg.connect(self._database_url) as conn:
            row = conn.execute("SELECT 1 FROM photos WHERE photo_id = %s", (photo_id,)).fetchone()
        return row is not None

    @contextmanager
    def album_transaction(self, album_id: int) -> Iterator["AlbumTransaction"]:
        with psycopg.connect(self._database_url) as conn:
            register_vector(conn)
            with conn.transaction():
                # 같은 앨범의 배정이 동시에 돌면 서로의 새 얼굴을 못 보고
                # 한 사람이 두 인물로 나뉜다. 메시지 키가 album_id 라 보통은 순차지만,
                # 워커 여러 개·리밸런스 순간을 대비해 앨범 단위로 줄 세운다.
                conn.execute("SELECT pg_advisory_xact_lock(%s)", (album_id,))
                yield AlbumTransaction(conn, album_id, datetime.now())


def _is_analyzed(conn: psycopg.Connection, photo_id: int) -> bool:
    row = conn.execute("SELECT 1 FROM face_analyses WHERE photo_id = %s", (photo_id,)).fetchone()
    return row is not None


class AlbumTransaction:
    """한 앨범에 대한 저장 트랜잭션. AlbumFaces 규약을 DB 로 구현한다."""

    def __init__(self, conn: psycopg.Connection, album_id: int, now: datetime):
        self._conn = conn
        self._album_id = album_id
        self._now = now

    def is_analyzed(self, photo_id: int) -> bool:
        return _is_analyzed(self._conn, photo_id)

    def nearest_faces(self, embedding: np.ndarray, k: int) -> list[Neighbor]:
        rows = self._conn.execute(
            """
            SELECT person_id, 1 - (embedding <=> %s) AS similarity
            FROM faces
            WHERE album_id = %s
            ORDER BY embedding <=> %s
            LIMIT %s
            """,
            (embedding, self._album_id, embedding, k),
        ).fetchall()
        return [Neighbor(person_id=row[0], similarity=float(row[1])) for row in rows]

    def create_person(self) -> int:
        return self._conn.execute(
            """
            INSERT INTO persons (album_id, created_at, modified_at)
            VALUES (%s, %s, %s)
            RETURNING person_id
            """,
            (self._album_id, self._now, self._now),
        ).fetchone()[0]

    def insert_face(self, photo_id: int, person_id: int, face: DetectedFace) -> int:
        x1, y1, x2, y2 = face.bbox
        return self._conn.execute(
            """
            INSERT INTO faces (photo_id, album_id, person_id, bbox_x1, bbox_y1, bbox_x2, bbox_y2,
                               det_score, embedding, created_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            RETURNING face_id
            """,
            (
                photo_id,
                self._album_id,
                person_id,
                x1,
                y1,
                x2,
                y2,
                face.det_score,
                face.embedding,
                self._now,
            ),
        ).fetchone()[0]

    def update_cover_if_better(self, person_id: int, face_id: int, det_score: float) -> None:
        self._conn.execute(
            """
            UPDATE persons p
            SET cover_face_id = %s, modified_at = %s
            WHERE p.person_id = %s
              AND (p.cover_face_id IS NULL
                   OR (SELECT f.det_score FROM faces f WHERE f.face_id = p.cover_face_id) < %s)
            """,
            (face_id, self._now, person_id, det_score),
        )

    def mark_analyzed(self, photo_id: int, face_count: int) -> None:
        self._conn.execute(
            """
            INSERT INTO face_analyses (photo_id, album_id, face_count, analyzed_at)
            VALUES (%s, %s, %s, %s)
            """,
            (photo_id, self._album_id, face_count, self._now),
        )
