from datetime import datetime

import psycopg
import pytest

from thumbnail_worker.photo_repository import PhotoRepository, PhotoRow

pytestmark = pytest.mark.integration


@pytest.fixture
def repository(config) -> PhotoRepository:
    return PhotoRepository(config.database_url)


def test_find_returns_unprocessed_row(repository, insert_photo, unique_album_id):
    photo_id, storage_key = insert_photo(unique_album_id)

    assert repository.find(photo_id) == PhotoRow(
        photo_id=photo_id,
        album_id=unique_album_id,
        storage_key=storage_key,
        preview_key=None,
        width=None,
        height=None,
        processed_at=None,
    )


def test_find_returns_none_for_missing_photo(repository):
    assert repository.find(987_654_321_000) is None


def test_mark_processed_stores_results(repository, insert_photo, unique_album_id, config):
    photo_id, _ = insert_photo(unique_album_id)
    processed_at = datetime(2026, 9, 28, 12, 0, 0)

    repository.mark_processed(
        photo_id, "t.jpg", "p.jpg", 4032, 3024, datetime(2026, 9, 1, 10, 20, 30), processed_at
    )

    row = repository.find(photo_id)
    assert (row.preview_key, row.width, row.height, row.processed_at) == (
        "p.jpg",
        4032,
        3024,
        processed_at,
    )
    with psycopg.connect(config.database_url) as conn:
        thumbnail_key, taken_at, modified_at = conn.execute(
            "SELECT thumbnail_key, taken_at, modified_at FROM photos WHERE photo_id = %s",
            (photo_id,),
        ).fetchone()
    assert (thumbnail_key, taken_at, modified_at) == (
        "t.jpg",
        datetime(2026, 9, 1, 10, 20, 30),
        processed_at,
    )
