import pytest

from thumbnail_worker.errors import PermanentError
from thumbnail_worker.worker import backoff_seconds, dlq_headers


@pytest.mark.parametrize(
    ("failures", "expected"), [(1, 1), (2, 2), (3, 4), (5, 16), (6, 30), (100, 30)]
)
def test_backoff_doubles_up_to_30_seconds(failures, expected):
    assert backoff_seconds(failures) == expected


def test_dlq_headers_describe_error_and_source():
    error = PermanentError("UNDECODABLE_IMAGE", "이미지를 읽을 수 없다")

    assert dlq_headers(error, "photo.uploaded", 2, 41) == [
        ("error-type", b"UNDECODABLE_IMAGE"),
        ("error-message", "이미지를 읽을 수 없다".encode()),
        ("source-topic", b"photo.uploaded"),
        ("source-partition", b"2"),
        ("source-offset", b"41"),
    ]


def test_long_error_message_is_truncated():
    error = PermanentError("INVALID_MESSAGE", "x" * 5000)

    headers = dict(dlq_headers(error, "photo.uploaded", 0, 0))

    assert len(headers["error-message"]) == 1000
