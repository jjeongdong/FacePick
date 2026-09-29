import json

import pytest

from face_worker.errors import PermanentError
from face_worker.messages import PreviewReady, parse_preview_ready

VALID = {
    "photoId": 7,
    "albumId": 3,
    "previewKey": "albums/3/previews/" + "a" * 64 + ".jpg",
    "width": 2048,
    "height": 1536,
}


def test_parses_valid_message():
    assert parse_preview_ready(json.dumps(VALID).encode()) == PreviewReady(
        photo_id=7, album_id=3, preview_key=VALID["previewKey"]
    )


@pytest.mark.parametrize(
    "value",
    [
        None,
        b"",
        b"not json",
        b"\xff\xfe",
        b"[1, 2]",
        json.dumps({**VALID, "photoId": None}).encode(),
        json.dumps({**VALID, "photoId": "7"}).encode(),
        json.dumps({**VALID, "photoId": True}).encode(),
        json.dumps({**VALID, "albumId": 0}).encode(),
        json.dumps({k: v for k, v in VALID.items() if k != "previewKey"}).encode(),
        json.dumps({**VALID, "previewKey": ""}).encode(),
        json.dumps({**VALID, "previewKey": None}).encode(),
    ],
)
def test_invalid_message_is_permanent_failure(value):
    with pytest.raises(PermanentError) as error:
        parse_preview_ready(value)
    assert error.value.error_type == "INVALID_MESSAGE"
