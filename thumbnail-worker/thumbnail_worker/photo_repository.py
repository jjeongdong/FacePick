from dataclasses import dataclass
from datetime import datetime

import psycopg


@dataclass(frozen=True)
class PhotoRow:
    photo_id: int
    album_id: int
    storage_key: str
    preview_key: str | None
    width: int | None
    height: int | None
    processed_at: datetime | None


class PhotoRepository:
    def __init__(self, database_url: str):
        self._database_url = database_url

    # 호출마다 연결한다. 연결 하나를 들고 있으면 DB 재시작 뒤 재시도가 끊긴 연결로 영원히 실패한다.
    def find(self, photo_id: int) -> PhotoRow | None:
        with psycopg.connect(self._database_url) as conn:
            row = conn.execute(
                """
                SELECT photo_id, album_id, storage_key, preview_key, width, height, processed_at
                FROM photos
                WHERE photo_id = %s
                """,
                (photo_id,),
            ).fetchone()
        return None if row is None else PhotoRow(*row)

    def mark_processed(
        self,
        photo_id: int,
        thumbnail_key: str,
        preview_key: str,
        width: int,
        height: int,
        taken_at: datetime | None,
        processed_at: datetime,
    ) -> None:
        with psycopg.connect(self._database_url) as conn:
            conn.execute(
                """
                UPDATE photos
                SET thumbnail_key = %s, preview_key = %s, width = %s, height = %s,
                    taken_at = %s, processed_at = %s, modified_at = %s
                WHERE photo_id = %s
                """,
                (
                    thumbnail_key,
                    preview_key,
                    width,
                    height,
                    taken_at,
                    processed_at,
                    processed_at,
                    photo_id,
                ),
            )
