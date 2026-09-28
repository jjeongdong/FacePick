import secrets
from collections.abc import Iterator

import boto3
import psycopg
import pytest
from botocore.config import Config as BotoConfig

from thumbnail_worker.config import Config, load_config


@pytest.fixture(scope="session")
def config() -> Config:
    return load_config()


@pytest.fixture(scope="session")
def s3_client(config: Config):
    client = boto3.client(
        "s3",
        endpoint_url=config.s3_endpoint,
        aws_access_key_id=config.s3_access_key,
        aws_secret_access_key=config.s3_secret_key,
        region_name="us-east-1",
        config=BotoConfig(s3={"addressing_style": "path"}),
    )
    # 백엔드를 띄운 적이 없어도 테스트가 돌도록 버킷을 보장한다.
    existing = {bucket["Name"] for bucket in client.list_buckets()["Buckets"]}
    if config.s3_bucket not in existing:
        client.create_bucket(Bucket=config.s3_bucket)
    return client


@pytest.fixture
def unique_album_id() -> int:
    # 개발 DB 의 실제 앨범과 겹치지 않는 큰 값. photos 는 albums 에 FK 가 없다.
    return 900_000_000 + secrets.randbelow(100_000_000)


@pytest.fixture
def insert_photo(config: Config) -> Iterator:
    inserted: list[int] = []

    def insert(album_id: int) -> tuple[int, str]:
        content_hash = secrets.token_hex(32)
        storage_key = f"albums/{album_id}/originals/{content_hash}"
        with psycopg.connect(config.database_url) as conn:
            photo_id = conn.execute(
                """
                INSERT INTO photos (album_id, uploader_id, content_hash, byte_size, content_type,
                                    storage_key, status, uploaded_at, created_at, modified_at)
                VALUES (%s, 1, %s, 1, 'image/jpeg', %s, 'UPLOADED', now(), now(), now())
                RETURNING photo_id
                """,
                (album_id, content_hash, storage_key),
            ).fetchone()[0]
        inserted.append(photo_id)
        return photo_id, storage_key

    yield insert
    with psycopg.connect(config.database_url) as conn:
        conn.execute("DELETE FROM photos WHERE photo_id = ANY(%s)", (inserted,))
