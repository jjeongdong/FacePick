import json

import pytest

from thumbnail_worker.errors import PermanentError
from thumbnail_worker.messages import PhotoUploaded, PreviewReady, parse_photo_uploaded

VALID = {
    "photoId": 7,
    "albumId": 3,
    "storageKey": "albums/3/originals/" + "a" * 64,
    "contentType": "image/jpeg",
    "byteSize": 1234,
}


def test_parses_valid_message():
    assert parse_photo_uploaded(json.dumps(VALID).encode()) == PhotoUploaded(
        photo_id=7, album_id=3, storage_key=VALID["storageKey"]
    )


@pytest.mark.parametrize(
    "value",
    [
        None,
        b"not json",
        b"\xff\xfe",
        b"[1, 2]",
        json.dumps({**VALID, "photoId": None}).encode(),
        json.dumps({**VALID, "photoId": "7"}).encode(),
        json.dumps({**VALID, "photoId": True}).encode(),
        json.dumps({**VALID, "albumId": 0}).encode(),
        json.dumps({k: v for k, v in VALID.items() if k != "storageKey"}).encode(),
        json.dumps({**VALID, "storageKey": ""}).encode(),
    ],
)
def test_invalid_message_is_permanent_failure(value):
    with pytest.raises(PermanentError) as error:
        parse_photo_uploaded(value)
    assert error.value.error_type == "INVALID_MESSAGE"


def test_preview_ready_serializes_contract_fields():
    event = PreviewReady(photo_id=7, album_id=3, preview_key="p.jpg", width=4032, height=3024)

    assert event.key() == "3"
    assert json.loads(event.to_json()) == {
        "photoId": 7,
        "albumId": 3,
        "previewKey": "p.jpg",
        "width": 4032,
        "height": 3024,
    }
