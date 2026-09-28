import json
import secrets
import time
from datetime import datetime
from io import BytesIO

import pytest
from confluent_kafka import Consumer, Producer
from PIL import Image

from thumbnail_worker.errors import PermanentError
from thumbnail_worker.photo_repository import PhotoRepository
from thumbnail_worker.processor import PhotoProcessor
from thumbnail_worker.storage import PhotoStorage
from thumbnail_worker.storage_keys import derived_keys

pytestmark = pytest.mark.integration


@pytest.fixture
def topic() -> str:
    # 실제 photo.preview_ready 를 더럽히지 않도록 테스트마다 새 토픽 (브로커 자동 생성)
    return f"test.preview_ready.{secrets.token_hex(6)}"


@pytest.fixture
def processor(config, s3_client, topic) -> PhotoProcessor:
    producer = Producer(
        {"bootstrap.servers": config.kafka_bootstrap, "acks": "all", "enable.idempotence": True}
    )
    storage = PhotoStorage(
        config.s3_endpoint, config.s3_access_key, config.s3_secret_key, config.s3_bucket
    )
    return PhotoProcessor(PhotoRepository(config.database_url), storage, producer, topic)


@pytest.fixture
def cleanup_keys(config, s3_client):
    keys: list[str] = []
    yield keys
    for key in keys:
        s3_client.delete_object(Bucket=config.s3_bucket, Key=key)


def consume_all(config, topic: str, timeout_seconds: float = 10.0) -> list:
    consumer = Consumer(
        {
            "bootstrap.servers": config.kafka_bootstrap,
            "group.id": f"test-{secrets.token_hex(6)}",
            "auto.offset.reset": "earliest",
        }
    )
    consumer.subscribe([topic])
    messages = []
    deadline = time.monotonic() + timeout_seconds
    try:
        while time.monotonic() < deadline:
            msg = consumer.poll(0.5)
            # 토픽이 아직 없을 때의 UNKNOWN_TOPIC 오류는 무시하고 계속 기다린다.
            if msg is None or msg.error():
                continue
            messages.append(msg)
            # 메시지를 받은 뒤 2초 더 기다려 중복 발행이 없는지 본다.
            deadline = time.monotonic() + 2
    finally:
        consumer.close()
    return messages


def uploaded_message(photo_id: int, album_id: int, storage_key: str) -> bytes:
    return json.dumps(
        {
            "photoId": photo_id,
            "albumId": album_id,
            "storageKey": storage_key,
            "contentType": "image/jpeg",
            "byteSize": 1,
        }
    ).encode()


def jpeg(size=(3000, 2000)) -> bytes:
    buffer = BytesIO()
    Image.new("RGB", size, "green").save(buffer, "JPEG")
    return buffer.getvalue()


def test_creates_renditions_updates_row_and_publishes(
    processor, config, s3_client, insert_photo, unique_album_id, topic, cleanup_keys
):
    photo_id, storage_key = insert_photo(unique_album_id)
    thumbnail_key, preview_key = derived_keys(storage_key)
    cleanup_keys.extend([storage_key, thumbnail_key, preview_key])
    s3_client.put_object(Bucket=config.s3_bucket, Key=storage_key, Body=jpeg())

    processor.handle(uploaded_message(photo_id, unique_album_id, storage_key))

    thumbnail = s3_client.get_object(Bucket=config.s3_bucket, Key=thumbnail_key)
    assert thumbnail["ContentType"] == "image/jpeg"
    assert Image.open(BytesIO(thumbnail["Body"].read())).size == (400, 267)
    preview = s3_client.get_object(Bucket=config.s3_bucket, Key=preview_key)
    assert Image.open(BytesIO(preview["Body"].read())).size == (2048, 1365)

    row = PhotoRepository(config.database_url).find(photo_id)
    assert (row.preview_key, row.width, row.height) == (preview_key, 3000, 2000)
    assert row.processed_at is not None

    messages = consume_all(config, topic)
    assert len(messages) == 1
    assert messages[0].key() == str(unique_album_id).encode()
    assert json.loads(messages[0].value()) == {
        "photoId": photo_id,
        "albumId": unique_album_id,
        "previewKey": preview_key,
        "width": 3000,
        "height": 2000,
    }


def test_already_processed_photo_is_only_republished(
    processor, config, s3_client, insert_photo, unique_album_id, topic
):
    photo_id, storage_key = insert_photo(unique_album_id)
    PhotoRepository(config.database_url).mark_processed(
        photo_id, "t.jpg", "p.jpg", 640, 480, None, datetime.now()
    )
    # 원본을 올리지 않았다: 이미지 작업을 하면 ORIGINAL_MISSING 으로 실패한다.

    processor.handle(uploaded_message(photo_id, unique_album_id, storage_key))

    messages = consume_all(config, topic)
    assert len(messages) == 1
    assert json.loads(messages[0].value()) == {
        "photoId": photo_id,
        "albumId": unique_album_id,
        "previewKey": "p.jpg",
        "width": 640,
        "height": 480,
    }


def test_missing_row_is_skipped_without_publishing(processor, config, unique_album_id, topic):
    storage_key = f"albums/{unique_album_id}/originals/{secrets.token_hex(32)}"

    processor.handle(uploaded_message(987_654_321_000, unique_album_id, storage_key))

    assert consume_all(config, topic, timeout_seconds=3) == []


def test_missing_original_is_permanent_failure(processor, insert_photo, unique_album_id):
    photo_id, storage_key = insert_photo(unique_album_id)

    with pytest.raises(PermanentError) as error:
        processor.handle(uploaded_message(photo_id, unique_album_id, storage_key))
    assert error.value.error_type == "ORIGINAL_MISSING"


def test_broken_original_is_permanent_failure(
    processor, config, s3_client, insert_photo, unique_album_id, cleanup_keys
):
    photo_id, storage_key = insert_photo(unique_album_id)
    cleanup_keys.append(storage_key)
    s3_client.put_object(Bucket=config.s3_bucket, Key=storage_key, Body=b"not an image")

    with pytest.raises(PermanentError) as error:
        processor.handle(uploaded_message(photo_id, unique_album_id, storage_key))
    assert error.value.error_type == "UNDECODABLE_IMAGE"
    assert PhotoRepository(config.database_url).find(photo_id).processed_at is None
