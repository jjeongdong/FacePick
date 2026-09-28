import pytest

from thumbnail_worker.errors import PermanentError
from thumbnail_worker.storage_keys import derived_keys

HASH = "0123456789abcdef" * 4


def test_derives_thumbnail_and_preview_keys():
    assert derived_keys(f"albums/12/originals/{HASH}") == (
        f"albums/12/thumbnails/{HASH}.jpg",
        f"albums/12/previews/{HASH}.jpg",
    )


@pytest.mark.parametrize(
    "storage_key",
    [
        f"albums/12/thumbnails/{HASH}",
        f"albums/x/originals/{HASH}",
        "albums/12/originals/short",
        f"albums/12/originals/{HASH}/extra",
        f"/albums/12/originals/{HASH}",
    ],
)
def test_unexpected_key_is_permanent_failure(storage_key):
    with pytest.raises(PermanentError) as error:
        derived_keys(storage_key)
    assert error.value.error_type == "INVALID_STORAGE_KEY"
