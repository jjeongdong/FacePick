import secrets

import pytest

from thumbnail_worker.errors import PermanentError
from thumbnail_worker.storage import PhotoStorage

pytestmark = pytest.mark.integration


@pytest.fixture
def storage(config, s3_client) -> PhotoStorage:
    return PhotoStorage(
        config.s3_endpoint, config.s3_access_key, config.s3_secret_key, config.s3_bucket
    )


@pytest.fixture
def test_key(config, s3_client):
    key = f"test/{secrets.token_hex(8)}.jpg"
    yield key
    s3_client.delete_object(Bucket=config.s3_bucket, Key=key)


def test_uploaded_jpeg_can_be_downloaded(storage, s3_client, config, test_key):
    storage.upload_jpeg(test_key, b"jpeg-bytes")

    assert storage.download(test_key) == b"jpeg-bytes"
    head = s3_client.head_object(Bucket=config.s3_bucket, Key=test_key)
    assert head["ContentType"] == "image/jpeg"


def test_missing_original_is_permanent_failure(storage):
    with pytest.raises(PermanentError) as error:
        storage.download(f"test/missing-{secrets.token_hex(8)}")
    assert error.value.error_type == "ORIGINAL_MISSING"
